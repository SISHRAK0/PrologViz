(ns logicflow.search
  "Backtracking search engine with lazy evaluation."
  (:require [logicflow.unify :as u]))

(defn succeed [subs] [subs])
(defn fail [_subs] [])
(def fail-goal (constantly []))
(def succeed-goal (fn [subs] [subs]))

(defn conj-goals [goal1 goal2]
  (fn [subs]
    (mapcat goal2 (goal1 subs))))

(defn disj-goals [goal1 goal2]
  (fn [subs]
    (lazy-cat (goal1 subs) (goal2 subs))))

(defn conj-all [& goals]
  (if (empty? goals)
    succeed-goal
    (reduce conj-goals goals)))

(defn disj-all [& goals]
  (if (empty? goals)
    fail-goal
    (reduce disj-goals goals)))

(defn fresh [n goal-fn]
  (fn [subs]
    (let [vars (repeatedly n #(u/lvar (gensym "v")))]
      ((apply goal-fn vars) subs))))

(defmacro fresh* [vars & goals]
  `(fresh ~(count vars)
     (fn [~@vars]
       (conj-all ~@goals))))

(defn conda [& clauses]
  (fn [subs]
    (loop [cs clauses]
      (if (empty? cs)
        []
        (let [[test & goals] (first cs)
              results (test subs)]
          (if (seq results)
            (mapcat (apply conj-all goals) results)
            (recur (rest cs))))))))

(defn condu [& clauses]
  (fn [subs]
    (loop [cs clauses]
      (if (empty? cs)
        []
        (let [[test & goals] (first cs)
              results (test subs)]
          (if (seq results)
            (take 1 (mapcat (apply conj-all goals) results))
            (recur (rest cs))))))))

(defn noto [goal]
  (fn [subs]
    (if (seq (goal subs))
      []
      [subs])))

(def ^:dynamic *cut-exception* nil)
(defrecord CutSignal [])

(defn cut []
  (fn [subs]
    (if *cut-exception*
      (throw (ex-info "cut" {:signal (CutSignal.) :subs subs}))
      [subs])))

(defn catch-cut [goal]
  (fn [subs]
    (binding [*cut-exception* true]
      (try
        (doall (goal subs))
        (catch clojure.lang.ExceptionInfo e
          (if (instance? CutSignal (:signal (ex-data e)))
            [(-> e ex-data :subs)]
            (throw e)))))))

(defn solve
  ([goals] (solve goals u/empty-subs))
  ([goals subs]
   (if (empty? goals)
     [subs]
     (let [[g & gs] goals]
       (mapcat #(solve gs %) (g subs))))))

(defn solve-n [n goals subs]
  (take n (solve goals subs)))

(defn solve-first [goals subs]
  (first (solve goals subs)))

(def ^:dynamic *trace-fn* nil)

(defn trace-goal [name goal]
  (fn [subs]
    (when *trace-fn*
      (*trace-fn* {:event :enter-goal :name name :subs subs}))
    (let [results (goal subs)]
      (when *trace-fn*
        (*trace-fn* {:event :exit-goal :name name :results (count (take 100 results))}))
      results)))

(defmacro with-trace [trace-fn & body]
  `(binding [*trace-fn* ~trace-fn]
     ~@body))

(defn run* [goal result-var]
  (for [subs (goal u/empty-subs)]
    (u/reify-term result-var subs)))

(defn run-n [n goal result-var]
  (take n (run* goal result-var)))

(defn run-1 [goal result-var]
  (first (run* goal result-var)))

(defn membero [x l]
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

(defn appendo [l1 l2 l3]
  (disj-goals
   (conj-goals (u/== l1 []) (u/== l2 l3))
   (fresh 3
     (fn [head tail res]
       (conj-all
        (u/== l1 (cons head tail))
        (u/== l3 (cons head res))
        (appendo tail l2 res))))))

(defn lengtho [l n]
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
              (let [new-n (u/lvar "n")]
                ((conj-all
                  (lengtho tail new-n)
                  (fn [s]
                    (let [tail-len (u/walk new-n s)]
                      (if (number? tail-len)
                        ((u/== n (inc tail-len)) s)
                        []))))
                 subs))))))))))
