(ns logicflow.trace
  "Tracing and debugging for inference visualization.
   Provides step-by-step execution tracking."
  (:require [logicflow.unify :as u]))

;; ============================================================================
;; Trace State
;; ============================================================================

(def ^:dynamic *tracing* false)
(def ^:dynamic *trace-depth* 0)
(def ^:dynamic *max-trace-depth* 50)

(def trace-log
  "Atom containing trace events for visualization."
  (atom []))

(def trace-tree
  "Atom containing inference tree structure."
  (atom {:nodes [] :edges []}))

(def node-counter (atom 0))

;; ============================================================================
;; Trace Events
;; ============================================================================

(defn clear-trace!
  "Clear all trace data."
  []
  (reset! trace-log [])
  (reset! trace-tree {:nodes [] :edges []})
  (reset! node-counter 0))

(defn- next-node-id []
  (swap! node-counter inc))

(defn log-event!
  "Log a trace event."
  [event]
  (when *tracing*
    (swap! trace-log conj
           (assoc event
                  :timestamp (System/currentTimeMillis)
                  :depth *trace-depth*))))

(defn add-tree-node!
  "Add a node to the inference tree."
  [node]
  (let [id (next-node-id)]
    (swap! trace-tree update :nodes conj (assoc node :id id))
    id))

(defn add-tree-edge!
  "Add an edge to the inference tree."
  [from to label]
  (swap! trace-tree update :edges conj {:from from :to to :label label}))

;; ============================================================================
;; Traced Goal Wrapper
;; ============================================================================

(defn trace-goal
  "Wrap a goal with tracing for visualization."
  [goal-name args goal]
  (fn [subs]
    (if (or (not *tracing*) (> *trace-depth* *max-trace-depth*))
      (goal subs)
      (let [node-id (add-tree-node! {:name goal-name
                                      :args (u/walk* args subs)
                                      :type :call
                                      :depth *trace-depth*})]
        (log-event! {:event :call
                     :goal goal-name
                     :args (u/walk* args subs)
                     :subs (into {} (map (fn [[k v]] [(str k) (u/walk* v subs)]) subs))
                     :node-id node-id})
        
        (binding [*trace-depth* (inc *trace-depth*)]
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

;; ============================================================================
;; Tracing Macros
;; ============================================================================

(defmacro with-tracing
  "Execute body with tracing enabled."
  [& body]
  `(binding [*tracing* true
             *trace-depth* 0]
     (clear-trace!)
     (let [result# (do ~@body)]
       result#)))

(defmacro trace
  "Trace a single goal execution."
  [goal-name args goal-expr]
  `(trace-goal ~goal-name ~args ~goal-expr))

;; ============================================================================
;; Trace Formatting
;; ============================================================================

(defn format-trace
  "Format trace log for display."
  []
  (for [{:keys [event goal args depth success result-count]} @trace-log]
    (let [indent (apply str (repeat depth "  "))
          args-str (pr-str args)]
      (case event
        :call (str indent "CALL: " goal " " args-str)
        :exit (str indent "EXIT: " goal " (" result-count " solutions)")
        :fail (str indent "FAIL: " goal)
        :redo (str indent "REDO: " goal)
        (str indent (name event) ": " goal)))))

(defn print-trace
  "Print formatted trace to console."
  []
  (doseq [line (format-trace)]
    (println line)))

;; ============================================================================
;; Inference Tree Export
;; ============================================================================

(defn get-trace-log
  "Get the current trace log."
  []
  @trace-log)

(defn get-trace-tree
  "Get the inference tree for visualization."
  []
  @trace-tree)

(defn export-trace-tree
  "Export trace tree in format suitable for D3.js visualization."
  []
  (let [{:keys [nodes edges]} @trace-tree]
    {:nodes (mapv (fn [{:keys [id name args type depth result-count]}]
                    {:id id
                     :label (str name)
                     :args (pr-str args)
                     :status (case type
                               :success "success"
                               :fail "fail"
                               :call "pending"
                               "unknown")
                     :depth depth
                     :results (or result-count 0)})
                  nodes)
     :links (mapv (fn [{:keys [from to label]}]
                    {:source from
                     :target to
                     :label (or label "")})
                  edges)}))

;; ============================================================================
;; Step-by-Step Execution
;; ============================================================================

(def step-state (atom {:paused false
                       :step-mode false
                       :current-step 0
                       :breakpoints #{}}))

(defn set-breakpoint!
  "Set a breakpoint on a predicate."
  [predicate-name]
  (swap! step-state update :breakpoints conj predicate-name))

(defn clear-breakpoint!
  "Clear a breakpoint."
  [predicate-name]
  (swap! step-state update :breakpoints disj predicate-name))

(defn clear-all-breakpoints!
  "Clear all breakpoints."
  []
  (swap! step-state assoc :breakpoints #{}))

(defn enable-step-mode!
  "Enable step-by-step execution."
  []
  (swap! step-state assoc :step-mode true :paused true))

(defn disable-step-mode!
  "Disable step-by-step execution."
  []
  (swap! step-state assoc :step-mode false :paused false))

(defn step!
  "Execute one step."
  []
  (swap! step-state update :current-step inc)
  (swap! step-state assoc :paused false)
  ;; Will pause again on next goal
  )

(defn continue!
  "Continue execution until next breakpoint."
  []
  (swap! step-state assoc :paused false :step-mode false))

;; ============================================================================
;; Statistics
;; ============================================================================

(defn trace-stats
  "Get statistics from trace log."
  []
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

