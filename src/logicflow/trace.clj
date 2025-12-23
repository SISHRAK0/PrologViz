(ns logicflow.trace
  "Tracing and debugging for inference visualization."
  (:require [logicflow.unify :as u]))

(def ^:dynamic *tracing* false)
(def ^:dynamic *trace-depth* 0)
(def ^:dynamic *max-trace-depth* 50)
(def ^:dynamic *parent-node-id* nil)

(def trace-log (atom []))
(def trace-tree (atom {:nodes [] :edges []}))
(def node-counter (atom 0))

(defn clear-trace! []
  (reset! trace-log [])
  (reset! trace-tree {:nodes [] :edges []})
  (reset! node-counter 0))

(defn- next-node-id []
  (swap! node-counter inc))

(defn log-event! [event]
  (when *tracing*
    (swap! trace-log conj
           (assoc event
                  :timestamp (System/currentTimeMillis)
                  :depth *trace-depth*))))

(defn add-tree-node! [node]
  (let [id (next-node-id)]
    (swap! trace-tree update :nodes conj (assoc node :id id))
    id))

(defn add-tree-edge! [from to label]
  (when (and from to)
    (swap! trace-tree update :edges conj {:from from :to to :label label})))

(defn trace-goal [goal-name args goal]
  (fn [subs]
    (if (or (not *tracing*) (> *trace-depth* *max-trace-depth*))
      (goal subs)
      (let [walked-args (u/walk* args subs)
            node-id (add-tree-node! {:name goal-name
                                      :args walked-args
                                      :type :call
                                      :depth *trace-depth*
                                      :parent *parent-node-id*})]
        ;; Add edge from parent to this node
        (add-tree-edge! *parent-node-id* node-id nil)
        
        (log-event! {:event :call
                     :goal goal-name
                     :args walked-args
                     :subs (into {} (map (fn [[k v]] [(str k) (u/walk* v subs)]) subs))
                     :node-id node-id
                     :parent-id *parent-node-id*})
        
        (binding [*trace-depth* (inc *trace-depth*)
                  *parent-node-id* node-id]
          (let [results (goal subs)
                result-seq (if (seq? results) results (seq results))]
            (if (seq result-seq)
              (do
                (log-event! {:event :exit
                             :goal goal-name
                             :success true
                             :result-count (count (take 100 result-seq))
                             :node-id node-id})
                (swap! trace-tree update :nodes
                       (fn [nodes]
                         (mapv #(if (= (:id %) node-id)
                                  (assoc % :type :success :result-count (count (take 10 result-seq)))
                                  %)
                               nodes)))
                result-seq)
              (do
                (log-event! {:event :fail
                             :goal goal-name
                             :node-id node-id})
                (swap! trace-tree update :nodes
                       (fn [nodes]
                         (mapv #(if (= (:id %) node-id)
                                  (assoc % :type :fail)
                                  %)
                               nodes)))
                []))))))))

(defmacro with-tracing [& body]
  `(binding [*tracing* true
             *trace-depth* 0]
     (clear-trace!)
     (let [result# (do ~@body)]
       result#)))

(defmacro trace [goal-name args goal-expr]
  `(trace-goal ~goal-name ~args ~goal-expr))

(defn format-trace []
  (for [{:keys [event goal args depth result-count]} @trace-log]
    (let [indent (apply str (repeat depth "  "))
          args-str (pr-str args)]
      (case event
        :call (str indent "CALL: " goal " " args-str)
        :exit (str indent "EXIT: " goal " (" result-count " solutions)")
        :fail (str indent "FAIL: " goal)
        :redo (str indent "REDO: " goal)
        (str indent (name event) ": " goal)))))

(defn print-trace []
  (doseq [line (format-trace)]
    (println line)))

(defn get-trace-log [] @trace-log)
(defn get-trace-tree [] @trace-tree)

(defn format-term
  "Format a term for display, converting LVars to ?name format."
  [term]
  (cond
    (u/lvar? term) (str "?" (:name term))
    (keyword? term) (str ":" (clojure.core/name term))
    (sequential? term) (str "[" (clojure.string/join " " (map format-term term)) "]")
    (map? term) (str "{" (clojure.string/join ", " (map (fn [[k v]] (str (format-term k) " " (format-term v))) term)) "}")
    :else (pr-str term)))

(defn export-trace-tree []
  (let [{:keys [nodes edges]} @trace-tree]
    {:nodes (mapv (fn [{:keys [id name args type depth result-count parent]}]
                    {:id id
                     :label (if (keyword? name) (clojure.core/name name) (str name))
                     :args (format-term args)
                     :status (case type
                               :success "success"
                               :fail "fail"
                               :call "pending"
                               "unknown")
                     :depth depth
                     :parent parent
                     :results (or result-count 0)})
                  nodes)
     :links (vec (keep (fn [{:keys [from to label]}]
                         (when (and from to)
                           {:source from
                            :target to
                            :label (or label "")}))
                       edges))}))

(def step-state (atom {:paused false
                       :step-mode false
                       :current-step 0
                       :breakpoints #{}}))

(defn set-breakpoint! [predicate-name]
  (swap! step-state update :breakpoints conj predicate-name))

(defn clear-breakpoint! [predicate-name]
  (swap! step-state update :breakpoints disj predicate-name))

(defn clear-all-breakpoints! []
  (swap! step-state assoc :breakpoints #{}))

(defn enable-step-mode! []
  (swap! step-state assoc :step-mode true :paused true))

(defn disable-step-mode! []
  (swap! step-state assoc :step-mode false :paused false))

(defn step! []
  (swap! step-state update :current-step inc)
  (swap! step-state assoc :paused false))

(defn continue! []
  (swap! step-state assoc :paused false :step-mode false))

(defn trace-stats []
  (let [log @trace-log
        calls (filter #(= :call (:event %)) log)
        successes (filter #(= :exit (:event %)) log)
        failures (filter #(= :fail (:event %)) log)]
    {:total-calls (count calls)
     :successes (count successes)
     :failures (count failures)
     :max-depth (apply max 0 (map :depth log))
     :predicates-called (frequencies (map :goal calls))
     :avg-results (if (seq successes)
                    (/ (reduce + (map :result-count successes))
                       (count successes))
                    0)}))

;; Spy Points
(def spy-points (atom #{}))
(def spy-log (atom []))

(defn spy! [predicate]
  (swap! spy-points conj (if (keyword? predicate) predicate (keyword predicate)))
  (println (str "Spy point set on: " predicate)))

(defn nospy! [predicate]
  (swap! spy-points disj (if (keyword? predicate) predicate (keyword predicate)))
  (println (str "Spy point removed from: " predicate)))

(defn nospy-all! []
  (reset! spy-points #{})
  (println "All spy points removed"))

(defn spying? [predicate]
  (let [pred-kw (if (keyword? predicate) predicate (keyword predicate))]
    (contains? @spy-points pred-kw)))

(defn get-spy-points [] @spy-points)

(defn clear-spy-log! []
  (reset! spy-log []))

(defn get-spy-log [] @spy-log)

(defn spy-goal [goal-name args goal]
  (fn [subs]
    (let [pred-kw (if (keyword? goal-name) goal-name (keyword goal-name))
          is-spied (spying? pred-kw)
          walked-args (u/walk* args subs)]
      (when is-spied
        (let [entry {:event :call
                     :goal pred-kw
                     :args walked-args
                     :timestamp (System/currentTimeMillis)}]
          (swap! spy-log conj entry)
          (println (str "  CALL: " (name pred-kw) " " (pr-str walked-args)))))
      
      (let [results (goal subs)
            result-seq (if (seq? results) results (seq results))]
        (when is-spied
          (if (seq result-seq)
            (let [entry {:event :exit
                         :goal pred-kw
                         :args walked-args
                         :result-count (count (take 100 result-seq))
                         :timestamp (System/currentTimeMillis)}]
              (swap! spy-log conj entry)
              (println (str "  EXIT: " (name pred-kw) " (" (count (take 100 result-seq)) " solutions)")))
            (let [entry {:event :fail
                         :goal pred-kw
                         :args walked-args
                         :timestamp (System/currentTimeMillis)}]
              (swap! spy-log conj entry)
              (println (str "  FAIL: " (name pred-kw))))))
        result-seq))))

(defn spy-stats []
  (let [log @spy-log
        calls (filter #(= :call (:event %)) log)
        exits (filter #(= :exit (:event %)) log)
        fails (filter #(= :fail (:event %)) log)]
    {:spy-points @spy-points
     :total-calls (count calls)
     :successes (count exits)
     :failures (count fails)
     :by-predicate (frequencies (map :goal calls))}))
