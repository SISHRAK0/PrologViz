(ns logicflow.kb
  "STM-based Knowledge Base for logic programming.
   Uses Clojure's Software Transactional Memory for concurrent access."
  (:require [logicflow.unify :as u]
            [logicflow.search :as s]
            [logicflow.trace :as trace]))

;; ============================================================================
;; Knowledge Base State (STM)
;; ============================================================================

(def facts
  "Ref containing facts indexed by predicate name.
   {predicate-name -> #{[args...] ...}}"
  (ref {}))

(def rules
  "Ref containing rules indexed by head predicate.
   {predicate-name -> [{:head [...] :body [...]} ...]}"
  (ref {}))

(def history
  "Ref containing transaction history for visualization.
   [{:type :assert/:retract/:query :data ... :timestamp ...} ...]"
  (ref []))

;; ============================================================================
;; Agents for Async Operations
;; ============================================================================

(def query-log
  "Agent for logging queries asynchronously."
  (agent []))

(def notification-agent
  "Agent for sending notifications to watchers."
  (agent {:watchers #{}}))

;; ============================================================================
;; Atoms for Caching
;; ============================================================================

(def query-cache
  "Atom for caching query results."
  (atom {}))

(def stats
  "Atom for tracking statistics."
  (atom {:queries 0
         :facts-asserted 0
         :facts-retracted 0
         :rules-added 0}))

;; ============================================================================
;; STM Operations
;; ============================================================================

(defn assert-fact!
  "Assert a fact into the knowledge base.
   Thread-safe via STM."
  [predicate args]
  (dosync
   (alter facts update predicate (fnil conj #{}) args)
   (alter history conj {:type :assert
                        :predicate predicate
                        :args args
                        :timestamp (System/currentTimeMillis)}))
  ;; Update stats (outside transaction)
  (swap! stats update :facts-asserted inc)
  ;; Invalidate cache
  (reset! query-cache {})
  {:status :ok :action :assert :predicate predicate :args args})

(defn retract-fact!
  "Retract a fact from the knowledge base.
   Thread-safe via STM."
  [predicate args]
  (dosync
   (alter facts update predicate (fnil disj #{}) args)
   (alter history conj {:type :retract
                        :predicate predicate
                        :args args
                        :timestamp (System/currentTimeMillis)}))
  (swap! stats update :facts-retracted inc)
  (reset! query-cache {})
  {:status :ok :action :retract :predicate predicate :args args})

(defn add-rule!
  "Add a rule to the knowledge base.
   Thread-safe via STM."
  [predicate head body]
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

(defn clear-kb!
  "Clear the entire knowledge base.
   Thread-safe via STM."
  []
  (dosync
   (ref-set facts {})
   (ref-set rules {})
   (alter history conj {:type :clear
                        :timestamp (System/currentTimeMillis)}))
  (reset! query-cache {})
  (reset! stats {:queries 0 :facts-asserted 0 :facts-retracted 0 :rules-added 0})
  {:status :ok :action :clear})

;; ============================================================================
;; Query Operations
;; ============================================================================

(defn get-facts
  "Get all facts for a predicate."
  [predicate]
  (get @facts predicate #{}))

(defn get-all-facts
  "Get all facts in the knowledge base."
  []
  @facts)

(defn get-rules
  "Get all rules for a predicate."
  [predicate]
  (get @rules predicate []))

(defn get-all-rules
  "Get all rules in the knowledge base."
  []
  @rules)

(defn get-history
  "Get the transaction history."
  ([] @history)
  ([n] (take-last n @history)))

(defn get-stats
  "Get knowledge base statistics."
  []
  (assoc @stats
         :total-facts (reduce + (map count (vals @facts)))
         :total-rules (reduce + (map count (vals @rules)))
         :predicates (into (set (keys @facts)) (keys @rules))))

;; ============================================================================
;; Goal Generation from KB
;; ============================================================================

(defn fact-goal
  "Create a goal that matches against facts for a predicate."
  [predicate pattern]
  (fn [subs]
    (let [matching-facts (get-facts predicate)]
      (mapcat (fn [fact-args]
                (when-let [s (u/unify pattern fact-args subs)]
                  [s]))
              matching-facts))))

(declare resolve-goal)

(defn rule-goal
  "Create a goal that tries to prove via rules for a predicate."
  [predicate pattern trace-info]
  (fn [subs]
    (let [matching-rules (get-rules predicate)]
      (mapcat (fn [{:keys [head body]}]
                ;; Rename variables in the rule to avoid conflicts
                (let [[head' var-map] (u/symbolize-term head)
                      [body' _] (reduce (fn [[bodies vmap] b]
                                          (let [[b' vmap'] (u/symbolize-term b vmap)]
                                            [(conj bodies b') vmap']))
                                        [[] var-map]
                                        body)]
                  (when-let [s (u/unify pattern head' subs)]
                    ;; Resolve body goals
                    (s/solve (map #(resolve-goal % trace-info) body') s))))
              matching-rules))))

(defn resolve-goal
  "Resolve a goal term to a goal function.
   Tries both facts and rules."
  ([term] (resolve-goal term nil))
  ([[predicate & args :as term] trace-info]
   (let [pred-kw (if (keyword? predicate)
                   predicate
                   (keyword (name predicate)))
         pattern (vec args)
         base-goal (fn [subs]
                     (lazy-cat
                      ((fact-goal pred-kw pattern) subs)
                      ((rule-goal pred-kw pattern trace-info) subs)))]
     ;; Wrap with tracing if enabled
     (if trace-info
       (trace/trace-goal pred-kw pattern base-goal)
       base-goal))))

;; ============================================================================
;; Query Interface
;; ============================================================================

(defn query
  "Execute a query against the knowledge base.
   Returns a lazy sequence of result bindings."
  [goal-terms & {:keys [trace? limit] :or {trace? false limit nil}}]
  (swap! stats update :queries inc)
  ;; Clear trace if tracing is enabled
  (when trace?
    (trace/clear-trace!))
  (binding [trace/*tracing* trace?]
    (let [;; Parse variables from goal terms
          [parsed-goals var-map] (reduce (fn [[goals vmap] term]
                                           (let [[term' vmap'] (u/symbolize-term term vmap)]
                                             [(conj goals term') vmap']))
                                         [[] {}]
                                         goal-terms)
          ;; Create goals - pass trace info when tracing is enabled
          trace-info (when trace? {:enabled true})
          goals (map #(resolve-goal % trace-info) parsed-goals)
          ;; Solve
          results (s/solve goals u/empty-subs)
          ;; Limit if specified
          results (if limit (take limit results) results)
          ;; Extract variable bindings
          var-names (into {} (map (fn [[k v]] [v k]) var-map))]
      ;; Force evaluation to ensure tracing captures everything
      (doall
       (for [subs results]
         (into {}
               (for [[name lvar] var-map]
                 [(keyword name) (u/reify-term lvar subs)])))))))

(defn query-one
  "Execute a query and return only the first result."
  [& goal-terms]
  (first (apply query goal-terms)))

;; ============================================================================
;; Watchers for Real-time Updates
;; ============================================================================

(defn add-watcher!
  "Add a watcher function that gets called on KB changes.
   The watcher fn receives {:type :event-type :data ...}"
  [watcher-id watcher-fn]
  (add-watch facts watcher-id
             (fn [_ _ old new]
               (when (not= old new)
                 (watcher-fn {:type :facts-changed :old old :new new}))))
  (add-watch rules watcher-id
             (fn [_ _ old new]
               (when (not= old new)
                 (watcher-fn {:type :rules-changed :old old :new new})))))

(defn remove-watcher!
  "Remove a watcher."
  [watcher-id]
  (remove-watch facts watcher-id)
  (remove-watch rules watcher-id))

;; ============================================================================
;; Validators
;; ============================================================================

(defn validate-fact
  "Validate a fact before assertion."
  [predicate args]
  (cond
    (not (keyword? predicate)) {:valid false :error "Predicate must be a keyword"}
    (not (sequential? args)) {:valid false :error "Arguments must be sequential"}
    :else {:valid true}))

(defn validate-rule
  "Validate a rule before addition."
  [predicate head body]
  (cond
    (not (keyword? predicate)) {:valid false :error "Predicate must be a keyword"}
    (not (sequential? head)) {:valid false :error "Head must be sequential"}
    (not (every? sequential? body)) {:valid false :error "Body must be a sequence of terms"}
    :else {:valid true}))

;; ============================================================================
;; Convenience Macros
;; ============================================================================

(defmacro with-kb
  "Execute body in context of a fresh knowledge base.
   Restores previous state after execution."
  [& body]
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

(defmacro with-temp-kb
  "Execute body with a temporary knowledge base.
   Changes are discarded after execution."
  [& body]
  `(binding [facts (ref {})
             rules (ref {})
             history (ref [])]
     ~@body))

;; ============================================================================
;; Serialization
;; ============================================================================

(defn export-kb
  "Export the knowledge base to a data structure."
  []
  {:facts @facts
   :rules @rules
   :exported-at (System/currentTimeMillis)})

(defn import-kb!
  "Import a knowledge base from a data structure."
  [{:keys [facts rules] :as kb-data}]
  (dosync
   (ref-set logicflow.kb/facts facts)
   (ref-set logicflow.kb/rules rules)
   (alter history conj {:type :import
                        :timestamp (System/currentTimeMillis)}))
  {:status :ok :action :import})

