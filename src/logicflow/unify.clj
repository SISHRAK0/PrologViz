(ns logicflow.unify
  "Unification algorithm for logic programming."
  (:refer-clojure :exclude [==]))

(defrecord LVar [name id]
  Object
  (toString [_] (str "?" name)))

(defn lvar
  ([name] (LVar. name (gensym)))
  ([name id] (LVar. name id)))

(defn lvar? [x]
  (instance? LVar x))

(defn lvar-name [lv]
  (when (lvar? lv) (:name lv)))

(def empty-subs {})

(defn walk [term subs]
  (if (lvar? term)
    (if-let [val (get subs term)]
      (recur val subs)
      term)
    term))

(defn walk* [term subs]
  (let [v (walk term subs)]
    (cond
      (lvar? v) v
      (sequential? v) (mapv #(walk* % subs) v)
      (map? v) (into {} (map (fn [[k val]] [(walk* k subs) (walk* val subs)]) v))
      :else v)))

(defn occurs? [var val subs]
  (let [v (walk val subs)]
    (cond
      (lvar? v) (= var v)
      (sequential? v) (some #(occurs? var % subs) v)
      (map? v) (or (some #(occurs? var % subs) (keys v))
                   (some #(occurs? var % subs) (vals v)))
      :else false)))

(defn extend-subs [var val subs]
  (if (occurs? var val subs)
    nil
    (assoc subs var val)))

(declare unify)

(defn unify-seq [s1 s2 subs]
  (cond
    (and (empty? s1) (empty? s2)) subs
    (or (empty? s1) (empty? s2)) nil
    :else
    (when-let [subs' (unify (first s1) (first s2) subs)]
      (recur (rest s1) (rest s2) subs'))))

(defn unify-map [m1 m2 subs]
  (if (not= (count m1) (count m2))
    nil
    (reduce (fn [s k]
              (if (nil? s)
                nil
                (if-let [v2 (get m2 k)]
                  (unify (get m1 k) v2 s)
                  nil)))
            subs
            (keys m1))))

(defn unify [term1 term2 subs]
  (let [t1 (walk term1 subs)
        t2 (walk term2 subs)]
    (cond
      (= t1 t2) subs
      (lvar? t1) (extend-subs t1 t2 subs)
      (lvar? t2) (extend-subs t2 t1 subs)
      (and (sequential? t1) (sequential? t2)) (unify-seq t1 t2 subs)
      (and (map? t1) (map? t2)) (unify-map t1 t2 subs)
      :else nil)))

(defn == [term1 term2]
  (fn [subs]
    (when-let [s (unify term1 term2 subs)]
      [s])))

(defn reify-name [n]
  (symbol (str "_" n)))

(defn reify-subs [term subs]
  (let [v (walk term subs)]
    (cond
      (lvar? v) (assoc subs v (reify-name (count subs)))
      (sequential? v) (reduce #(reify-subs %2 %1) subs v)
      (map? v) (reduce #(reify-subs %2 %1) subs (concat (keys v) (vals v)))
      :else subs)))

(defn reify-term [term subs]
  (let [v (walk* term subs)]
    (walk* v (reify-subs v empty-subs))))

(defn parse-lvar [sym]
  (when (and (symbol? sym)
             (= \? (first (name sym))))
    (lvar (subs (name sym) 1))))

(defn symbolize-term
  ([term] (symbolize-term term {}))
  ([term var-map]
   (cond
     (and (symbol? term) (= \? (first (name term))))
     (let [var-name (subs (name term) 1)]
       (if-let [existing (get var-map var-name)]
         [existing var-map]
         (let [new-var (lvar var-name)]
           [new-var (assoc var-map var-name new-var)])))
     
     (sequential? term)
     (let [[result vmap] (reduce (fn [[acc vmap] t]
                                   (let [[t' vmap'] (symbolize-term t vmap)]
                                     [(conj acc t') vmap']))
                                 [[] var-map]
                                 term)]
       [result vmap])
     
     (map? term)
     (reduce (fn [[acc vmap] [k v]]
               (let [[k' vmap'] (symbolize-term k vmap)
                     [v' vmap''] (symbolize-term v vmap')]
                 [(assoc acc k' v') vmap'']))
             [{} var-map]
             term)
     
     :else [term var-map])))

(defn vars-in-term [term]
  (cond
    (lvar? term) #{term}
    (sequential? term) (reduce into #{} (map vars-in-term term))
    (map? term) (reduce into #{} (map vars-in-term (concat (keys term) (vals term))))
    :else #{}))
