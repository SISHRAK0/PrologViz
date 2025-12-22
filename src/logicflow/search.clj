(ns logicflow.search
  "Backtracking search engine for logic programming.
   Implements goal-based search with lazy evaluation."
  (:require [logicflow.unify :as u]))

;; ============================================================================
;; Goal Combinators
;; ============================================================================

(defn succeed
  "A goal that always succeeds with the given substitution."
  [subs]
  [subs])

(defn fail
  "A goal that always fails."
  [_subs]
  [])

(def fail-goal
  "A goal function that always fails."
  (constantly []))

(def succeed-goal
  "A goal function that always succeeds."
  (fn [subs] [subs]))

(defn conj-goals
  "Conjunction: both goals must succeed (AND).
   Returns a lazy sequence of substitutions."
  [goal1 goal2]
  (fn [subs]
    (mapcat goal2 (goal1 subs))))

(defn disj-goals
  "Disjunction: either goal can succeed (OR).
   Returns a lazy sequence combining both branches."
  [goal1 goal2]
  (fn [subs]
    (lazy-cat (goal1 subs) (goal2 subs))))

(defn conj-all
  "Conjunction of multiple goals."
  [& goals]
  (if (empty? goals)
    succeed-goal
    (reduce conj-goals goals)))

(defn disj-all
  "Disjunction of multiple goals."
  [& goals]
  (if (empty? goals)
    fail-goal
    (reduce disj-goals goals)))

;; ============================================================================
;; Fresh Variables
;; ============================================================================

(defn fresh
  "Create fresh logic variables and apply them to a goal function.
   The goal-fn should accept n arguments (the fresh variables)."
  [n goal-fn]
  (fn [subs]
    (let [vars (repeatedly n #(u/lvar (gensym "v")))]
      ((apply goal-fn vars) subs))))

(defmacro fresh*
  "Macro for fresh variables with names.
   Usage: (fresh* [x y z] (== x 1) (== y 2))"
  [vars & goals]
  `(fresh ~(count vars)
     (fn [~@vars]
       (conj-all ~@goals))))

;; ============================================================================
;; Conditional Goals
;; ============================================================================

(defn conda
  "Soft cut: if first goal succeeds, commit to that branch."
  [& clauses]
  (fn [subs]
    (loop [cs clauses]
      (if (empty? cs)
        []
        (let [[test & goals] (first cs)
              results (test subs)]
          (if (seq results)
            (mapcat (apply conj-all goals) results)
            (recur (rest cs))))))))

(defn condu
  "Committed choice: like conda but takes only first result."
  [& clauses]
  (fn [subs]
    (loop [cs clauses]
      (if (empty? cs)
        []
        (let [[test & goals] (first cs)
              results (test subs)]
          (if (seq results)
            (take 1 (mapcat (apply conj-all goals) results))
            (recur (rest cs))))))))

;; ============================================================================
;; Negation
;; ============================================================================

(defn noto
  "Negation as failure: succeed if goal fails, fail if goal succeeds."
  [goal]
  (fn [subs]
    (if (seq (goal subs))
      []
      [subs])))

;; ============================================================================
;; Cut
;; ============================================================================

(def ^:dynamic *cut-exception* nil)

(defrecord CutSignal [])

(defn cut
  "Cut: prune the search tree."
  []
  (fn [subs]
    (if *cut-exception*
      (throw (ex-info "cut" {:signal (CutSignal.) :subs subs}))
      [subs])))

(defn catch-cut
  "Catch cut exceptions within a scope."
  [goal]
  (fn [subs]
    (binding [*cut-exception* true]
      (try
        (doall (goal subs))
        (catch clojure.lang.ExceptionInfo e
          (if (instance? CutSignal (:signal (ex-data e)))
            [(-> e ex-data :subs)]
            (throw e)))))))

;; ============================================================================
;; Search Strategies
;; ============================================================================

(defn solve
  "Solve a sequence of goals with the given substitution.
   Returns a lazy sequence of successful substitutions."
  ([goals] (solve goals u/empty-subs))
  ([goals subs]
   (if (empty? goals)
     [subs]
     (let [[g & gs] goals]
       (mapcat #(solve gs %) (g subs))))))

(defn solve-n
  "Solve for at most n solutions."
  [n goals subs]
  (take n (solve goals subs)))

(defn solve-first
  "Solve for the first solution only."
  [goals subs]
  (first (solve goals subs)))

;; ============================================================================
;; Goal with Tracing (for visualization)
;; ============================================================================

(def ^:dynamic *trace-fn* nil)

(defn trace-goal
  "Wrap a goal with tracing for visualization."
  [name goal]
  (fn [subs]
    (when *trace-fn*
      (*trace-fn* {:event :enter-goal :name name :subs subs}))
    (let [results (goal subs)]
      (when *trace-fn*
        (*trace-fn* {:event :exit-goal :name name :results (count (take 100 results))}))
      results)))

(defmacro with-trace
  "Execute goals with tracing enabled."
  [trace-fn & body]
  `(binding [*trace-fn* ~trace-fn]
     ~@body))

;; ============================================================================
;; Utilities
;; ============================================================================

(defn run*
  "Run a goal and return all solutions, reifying the result variable."
  [goal result-var]
  (for [subs (goal u/empty-subs)]
    (u/reify-term result-var subs)))

(defn run-n
  "Run a goal and return at most n solutions."
  [n goal result-var]
  (take n (run* goal result-var)))

(defn run-1
  "Run a goal and return the first solution."
  [goal result-var]
  (first (run* goal result-var)))

;; ============================================================================
;; Relational Arithmetic (for demo)
;; ============================================================================

(defn membero
  "Relational membership: x is a member of list l."
  [x l]
  (fn [subs]
    (let [l' (u/walk l subs)]
      (cond
        (empty? l') []
        (sequential? l')
        ((disj-goals
          (u/== x (first l'))
          (membero x (rest l')))
         subs)
        :else []))))

(defn appendo
  "Relational append: l3 is the concatenation of l1 and l2."
  [l1 l2 l3]
  (disj-goals
   (conj-goals (u/== l1 []) (u/== l2 l3))
   (fresh 3
     (fn [head tail res]
       (conj-all
        (u/== l1 (cons head tail))
        (u/== l3 (cons head res))
        (appendo tail l2 res))))))

(defn lengtho
  "Relational length: n is the length of list l."
  [l n]
  (disj-goals
   (conj-goals (u/== l []) (u/== n 0))
   (fresh 2
     (fn [head tail]
       (conj-all
        (u/== l (cons head tail))
        (fn [subs]
          (let [n' (u/walk n subs)]
            (if (number? n')
              ((conj-goals
                (u/== true (> n' 0))
                (lengtho tail (dec n')))
               subs)
              ;; Generate mode
              (let [new-n (u/lvar "n")]
                ((conj-all
                  (lengtho tail new-n)
                  (fn [s]
                    (let [tail-len (u/walk new-n s)]
                      (if (number? tail-len)
                        ((u/== n (inc tail-len)) s)
                        []))))
                 subs))))))))))

