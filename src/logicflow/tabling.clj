(ns logicflow.tabling
  "Tabling (memoization) for recursive predicates.
   Prevents infinite loops and improves performance.
   Similar to XSB Prolog's tabling feature."
  (:require [logicflow.unify :as u]))

;; ============================================================================
;; Table Cache
;; ============================================================================

(def ^:dynamic *tabling-enabled* true)

(def table-cache
  "Cache for tabled predicates: {predicate-name -> {args-pattern -> results}}"
  (atom {}))

(def table-stats
  "Statistics for tabling."
  (atom {:hits 0
         :misses 0
         :entries 0}))

;; ============================================================================
;; Cache Management
;; ============================================================================

(defn clear-tables!
  "Clear all tabling caches."
  []
  (reset! table-cache {})
  (reset! table-stats {:hits 0 :misses 0 :entries 0}))

(defn clear-table!
  "Clear cache for a specific predicate."
  [predicate-name]
  (swap! table-cache dissoc predicate-name))

(defn get-table-stats
  "Get tabling statistics."
  []
  (let [stats @table-stats
        total (+ (:hits stats) (:misses stats))]
    (assoc stats
           :hit-rate (if (pos? total)
                       (double (/ (:hits stats) total))
                       0.0)
           :cache-size (count @table-cache))))

;; ============================================================================
;; Tabled Goal Implementation
;; ============================================================================

(defn- make-cache-key
  "Create a cache key from substituted arguments."
  [args subs]
  (u/walk* args subs))

(defn- lookup-cache
  "Look up results in cache."
  [pred-name args subs]
  (let [key (make-cache-key args subs)]
    (get-in @table-cache [pred-name key])))

(defn- store-cache!
  "Store results in cache."
  [pred-name args subs results]
  (let [key (make-cache-key args subs)]
    (swap! table-cache assoc-in [pred-name key] results)
    (swap! table-stats update :entries inc)))

(defn tabled-goal
  "Wrap a goal with tabling/memoization.
   
   Usage:
   (def ancestor-tabled
     (tabled-goal :ancestor
       (fn [x y]
         (disj-goals
           (parent-goal x y)
           (fresh [z]
             (conj-all
               (parent-goal x z)
               (ancestor-tabled z y)))))))"
  [pred-name goal-fn]
  (fn [& args]
    (fn [subs]
      (if-not *tabling-enabled*
        ((apply goal-fn args) subs)
        (if-let [cached (lookup-cache pred-name args subs)]
          (do
            (swap! table-stats update :hits inc)
            cached)
          (do
            (swap! table-stats update :misses inc)
            (let [results (doall ((apply goal-fn args) subs))]
              (store-cache! pred-name args subs results)
              results)))))))

;; ============================================================================
;; Macro for Defining Tabled Predicates
;; ============================================================================

(defmacro deftabled
  "Define a tabled predicate with automatic memoization.
   
   Usage:
   (deftabled fib [n result]
     (disj-all
       (conj-all (== n 0) (== result 0))
       (conj-all (== n 1) (== result 1))
       (fresh [n1 n2 r1 r2]
         (conj-all
           (is n1 (- n 1))
           (is n2 (- n 2))
           (fib n1 r1)
           (fib n2 r2)
           (is result (+ r1 r2))))))"
  [name args & body]
  (let [pred-key (keyword name)]
    `(def ~name
       (tabled-goal ~pred-key
         (fn ~args ~@body)))))

;; ============================================================================
;; Subsumption Checking (Advanced Tabling)
;; ============================================================================

(defn subsumes?
  "Check if pattern1 subsumes pattern2 (more general)."
  [pattern1 pattern2]
  (let [subs (u/unify pattern1 pattern2 u/empty-subs)]
    (boolean subs)))

(defn find-subsuming-entry
  "Find a cache entry that subsumes the given pattern."
  [pred-name args subs]
  (let [key (make-cache-key args subs)
        entries (get @table-cache pred-name {})]
    (first
     (for [[cached-key cached-results] entries
           :when (subsumes? cached-key key)]
       cached-results))))

(defn tabled-goal-with-subsumption
  "Tabled goal with subsumption checking for more cache hits."
  [pred-name goal-fn]
  (fn [& args]
    (fn [subs]
      (if-not *tabling-enabled*
        ((apply goal-fn args) subs)
        (if-let [cached (or (lookup-cache pred-name args subs)
                            (find-subsuming-entry pred-name args subs))]
          (do
            (swap! table-stats update :hits inc)
            cached)
          (do
            (swap! table-stats update :misses inc)
            (let [results (doall ((apply goal-fn args) subs))]
              (store-cache! pred-name args subs results)
              results)))))))

;; ============================================================================
;; Variant Tabling (for infinite/recursive structures)
;; ============================================================================

(def variant-table
  "Table for variant checking (handles cycles)."
  (atom {}))

(defn- term-variant?
  "Check if two terms are variants (same up to variable renaming)."
  [t1 t2]
  (let [subs1 (u/unify t1 t2 u/empty-subs)
        subs2 (u/unify t2 t1 u/empty-subs)]
    (and subs1 subs2)))

(defn with-variant-check
  "Execute goal with variant checking to handle cycles."
  [pred-name args goal]
  (fn [subs]
    (let [key [pred-name (u/walk* args subs)]]
      (if (contains? @variant-table key)
        ;; Already computing this - return empty to break cycle
        []
        (do
          (swap! variant-table assoc key :computing)
          (let [results ((goal) subs)]
            (swap! variant-table assoc key results)
            results))))))

;; ============================================================================
;; Mode-directed Tabling
;; ============================================================================

(defn mode-tabled-goal
  "Tabled goal with mode declarations.
   Modes: :in (input/bound), :out (output/free)
   
   Example: (mode-tabled-goal :fib [:in :out] fib-impl)"
  [pred-name modes goal-fn]
  (fn [& args]
    (fn [subs]
      (let [;; Only cache based on input arguments
            input-args (map (fn [arg mode]
                             (if (= mode :in)
                               (u/walk* arg subs)
                               '_))
                           args modes)
            cache-key [pred-name input-args]]
        (if-let [cached (get @table-cache cache-key)]
          (do
            (swap! table-stats update :hits inc)
            (mapcat (fn [cached-subs]
                      ((apply u/== args (vals cached-subs)) subs))
                    cached))
          (do
            (swap! table-stats update :misses inc)
            (let [results (doall ((apply goal-fn args) subs))
                  output-vals (map (fn [s]
                                    (zipmap (map-indexed (fn [i _] i) args)
                                           (map #(u/walk* % s) args)))
                                  results)]
              (swap! table-cache assoc cache-key output-vals)
              results)))))))

;; ============================================================================
;; Utilities
;; ============================================================================

(defmacro with-tabling
  "Execute body with tabling enabled."
  [& body]
  `(binding [*tabling-enabled* true]
     ~@body))

(defmacro without-tabling
  "Execute body with tabling disabled."
  [& body]
  `(binding [*tabling-enabled* false]
     ~@body))

(defn table-info
  "Get information about tabled predicates."
  []
  {:predicates (keys @table-cache)
   :entries (reduce + (map count (vals @table-cache)))
   :stats @table-stats})

