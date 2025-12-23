(ns logicflow.kb
  "STM-based Knowledge Base for logic programming."
  (:require [logicflow.unify :as u]
            [logicflow.search :as s]
            [logicflow.trace :as trace]))

;; STM State
(def facts (ref {}))
(def rules (ref {}))
(def history (ref []))

;; Async agents
(def query-log (agent []))
(def notification-agent (agent {:watchers #{}}))

;; Caching & stats
(def query-cache (atom {}))
(def stats (atom {:queries 0 :facts-asserted 0 :facts-retracted 0 :rules-added 0}))

(defn assert-fact! [predicate args]
  (dosync
   (alter facts update predicate (fnil conj #{}) args)
   (alter history conj {:type :assert
                        :predicate predicate
                        :args args
                        :timestamp (System/currentTimeMillis)}))
  (swap! stats update :facts-asserted inc)
  (reset! query-cache {})
  {:status :ok :action :assert :predicate predicate :args args})

(defn retract-fact! [predicate args]
  (dosync
   (alter facts update predicate (fnil disj #{}) args)
   (alter history conj {:type :retract
                        :predicate predicate
                        :args args
                        :timestamp (System/currentTimeMillis)}))
  (swap! stats update :facts-retracted inc)
  (reset! query-cache {})
  {:status :ok :action :retract :predicate predicate :args args})

(defn add-rule! [predicate head body]
  (dosync
   (alter rules update predicate (fnil conj [])
          {:head head :body body :id (gensym "rule-")})
   (alter history conj {:type :add-rule
                        :predicate predicate
                        :head head
                        :body body
                        :timestamp (System/currentTimeMillis)}))
  (swap! stats update :rules-added inc)
  (reset! query-cache {})
  {:status :ok :action :add-rule :predicate predicate})

(defn clear-kb! []
  (dosync
   (ref-set facts {})
   (ref-set rules {})
   (alter history conj {:type :clear :timestamp (System/currentTimeMillis)}))
  (reset! query-cache {})
  (reset! stats {:queries 0 :facts-asserted 0 :facts-retracted 0 :rules-added 0})
  {:status :ok :action :clear})

(defn get-facts [predicate]
  (get @facts predicate #{}))

(defn get-all-facts [] @facts)

(defn get-rules [predicate]
  (get @rules predicate []))

(defn get-all-rules [] @rules)

(defn get-history
  ([] @history)
  ([n] (take-last n @history)))

(defn get-stats []
  (assoc @stats
         :total-facts (reduce + (map count (vals @facts)))
         :total-rules (reduce + (map count (vals @rules)))
         :predicates (into (set (keys @facts)) (keys @rules))))

(defn fact-goal [predicate pattern]
  (fn [subs]
    (let [matching-facts (get-facts predicate)]
      (mapcat (fn [fact-args]
                (when-let [s (u/unify pattern fact-args subs)]
                  [s]))
              matching-facts))))

(declare resolve-goal)

(defn rule-goal [predicate pattern trace-info]
  (fn [subs]
    (let [matching-rules (get-rules predicate)]
      (mapcat (fn [{:keys [head body]}]
                (let [[head' var-map] (u/symbolize-term head)
                      [body' _] (reduce (fn [[bodies vmap] b]
                                          (let [[b' vmap'] (u/symbolize-term b vmap)]
                                            [(conj bodies b') vmap']))
                                        [[] var-map]
                                        body)]
                  (when-let [s (u/unify pattern head' subs)]
                    (s/solve (map #(resolve-goal % trace-info) body') s))))
              matching-rules))))

(defn resolve-goal
  ([term] (resolve-goal term nil))
  ([[predicate & args :as _term] trace-info]
   (let [pred-kw (if (keyword? predicate)
                   predicate
                   (keyword (name predicate)))
         pattern (vec args)
         base-goal (fn [subs]
                     (lazy-cat
                      ((fact-goal pred-kw pattern) subs)
                      ((rule-goal pred-kw pattern trace-info) subs)))
         ;; Apply tracing if enabled
         traced-goal (if trace-info
                       (trace/trace-goal pred-kw pattern base-goal)
                       base-goal)]
     ;; Apply spy if predicate is being spied
     (if (trace/spying? pred-kw)
       (trace/spy-goal pred-kw pattern traced-goal)
       traced-goal))))

(defn query [goal-terms & {:keys [trace? limit] :or {trace? false limit nil}}]
  (swap! stats update :queries inc)
  (when trace?
    (trace/clear-trace!))
  (binding [trace/*tracing* trace?]
    (let [[parsed-goals var-map] (reduce (fn [[goals vmap] term]
                                           (let [[term' vmap'] (u/symbolize-term term vmap)]
                                             [(conj goals term') vmap']))
                                         [[] {}]
                                         goal-terms)
          trace-info (when trace? {:enabled true})
          goals (map #(resolve-goal % trace-info) parsed-goals)
          results (s/solve goals u/empty-subs)
          results (if limit (take limit results) results)]
      (doall
       (for [subs results]
         (into {}
               (for [[name lvar] var-map]
                 [(keyword name) (u/reify-term lvar subs)])))))))

(defn query-one [& goal-terms]
  (first (apply query goal-terms)))

(defn add-watcher! [watcher-id watcher-fn]
  (add-watch facts watcher-id
             (fn [_ _ old new]
               (when (not= old new)
                 (watcher-fn {:type :facts-changed :old old :new new}))))
  (add-watch rules watcher-id
             (fn [_ _ old new]
               (when (not= old new)
                 (watcher-fn {:type :rules-changed :old old :new new})))))

(defn remove-watcher! [watcher-id]
  (remove-watch facts watcher-id)
  (remove-watch rules watcher-id))

(defn validate-fact [predicate args]
  (cond
    (not (keyword? predicate)) {:valid false :error "Predicate must be a keyword"}
    (not (sequential? args)) {:valid false :error "Arguments must be sequential"}
    :else {:valid true}))

(defn validate-rule [predicate head body]
  (cond
    (not (keyword? predicate)) {:valid false :error "Predicate must be a keyword"}
    (not (sequential? head)) {:valid false :error "Head must be sequential"}
    (not (every? sequential? body)) {:valid false :error "Body must be a sequence of terms"}
    :else {:valid true}))

(defmacro with-kb [& body]
  `(let [old-facts# @facts
         old-rules# @rules
         old-history# @history]
     (try
       (clear-kb!)
       ~@body
       (finally
         (dosync
          (ref-set facts old-facts#)
          (ref-set rules old-rules#)
          (ref-set history old-history#))))))

(defmacro with-temp-kb [& body]
  `(binding [facts (ref {})
             rules (ref {})
             history (ref [])]
     ~@body))

(defn export-kb []
  {:facts @facts
   :rules @rules
   :exported-at (System/currentTimeMillis)})

(defn import-kb! [{:keys [facts rules]}]
  (dosync
   (ref-set logicflow.kb/facts facts)
   (ref-set logicflow.kb/rules rules)
   (alter history conj {:type :import :timestamp (System/currentTimeMillis)}))
  {:status :ok :action :import})
