(ns logicflow.unify
  "Unification algorithm for logic programming.
   Implements pattern matching with logic variables."
  (:refer-clojure :exclude [==]))

;; ============================================================================
;; Logic Variables
;; ============================================================================

(defrecord LVar [name id]
  Object
  (toString [_] (str "?" name)))

(defn lvar
  "Create a logic variable with the given name."
  ([name] (LVar. name (gensym)))
  ([name id] (LVar. name id)))

(defn lvar?
  "Check if x is a logic variable."
  [x]
  (instance? LVar x))

(defn lvar-name
  "Get the name of a logic variable."
  [lv]
  (when (lvar? lv) (:name lv)))

;; ============================================================================
;; Substitution Environment
;; ============================================================================

(def empty-subs
  "Empty substitution environment."
  {})

(defn walk
  "Walk a term through substitutions, following variable bindings.
   Returns the most specific value for the term."
  [term subs]
  (if (lvar? term)
    (if-let [val (get subs term)]
      (recur val subs)
      term)
    term))

(defn walk*
  "Deeply walk a term, resolving all variables in nested structures."
  [term subs]
  (let [v (walk term subs)]
    (cond
      (lvar? v) v
      (sequential? v) (mapv #(walk* % subs) v)
      (map? v) (into {} (map (fn [[k val]] [(walk* k subs) (walk* val subs)]) v))
      :else v)))

(defn occurs?
  "Occurs check: ensure var doesn't appear in val.
   Prevents infinite structures."
  [var val subs]
  (let [v (walk val subs)]
    (cond
      (lvar? v) (= var v)
      (sequential? v) (some #(occurs? var % subs) v)
      (map? v) (or (some #(occurs? var % subs) (keys v))
                   (some #(occurs? var % subs) (vals v)))
      :else false)))

(defn extend-subs
  "Extend substitution with a new binding.
   Returns nil if occurs check fails."
  [var val subs]
  (if (occurs? var val subs)
    nil
    (assoc subs var val)))

;; ============================================================================
;; Unification
;; ============================================================================

(declare unify)

(defn unify-seq
  "Unify two sequences element by element."
  [s1 s2 subs]
  (cond
    (and (empty? s1) (empty? s2)) subs
    (or (empty? s1) (empty? s2)) nil
    :else
    (when-let [subs' (unify (first s1) (first s2) subs)]
      (recur (rest s1) (rest s2) subs'))))

(defn unify-map
  "Unify two maps."
  [m1 m2 subs]
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

(defn unify
  "Unify two terms given a substitution environment.
   Returns updated substitution or nil if unification fails."
  [term1 term2 subs]
  (let [t1 (walk term1 subs)
        t2 (walk term2 subs)]
    (cond
      ;; Same values unify trivially
      (= t1 t2) subs
      
      ;; Logic variable bindings
      (lvar? t1) (extend-subs t1 t2 subs)
      (lvar? t2) (extend-subs t2 t1 subs)
      
      ;; Sequence unification
      (and (sequential? t1) (sequential? t2))
      (unify-seq t1 t2 subs)
      
      ;; Map unification
      (and (map? t1) (map? t2))
      (unify-map t1 t2 subs)
      
      ;; Failure
      :else nil)))

(defn ==
  "Create a unification goal."
  [term1 term2]
  (fn [subs]
    (when-let [s (unify term1 term2 subs)]
      [s])))

;; ============================================================================
;; Reification
;; ============================================================================

(defn reify-name
  "Generate a reified variable name."
  [n]
  (symbol (str "_" n)))

(defn reify-subs
  "Create reification substitution for remaining logic variables."
  [term subs]
  (let [v (walk term subs)]
    (cond
      (lvar? v) (let [n (count subs)]
                  (assoc subs v (reify-name n)))
      (sequential? v) (reduce #(reify-subs %2 %1) subs v)
      (map? v) (reduce #(reify-subs %2 %1) subs (concat (keys v) (vals v)))
      :else subs)))

(defn reify-term
  "Reify a term: resolve all bindings and name remaining variables."
  [term subs]
  (let [v (walk* term subs)]
    (walk* v (reify-subs v empty-subs))))

;; ============================================================================
;; Utilities
;; ============================================================================

(defn parse-lvar
  "Parse a symbol starting with ? into a logic variable."
  [sym]
  (when (and (symbol? sym)
             (= \? (first (name sym))))
    (lvar (subs (name sym) 1))))

(defn symbolize-term
  "Convert a term with ?-prefixed symbols to logic variables.
   Uses a variable map to ensure same symbols get same variables."
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
     ;; Use vector for accumulation to preserve order, then convert back if needed
     (let [[result vmap] (reduce (fn [[acc vmap] t]
                                   (let [[t' vmap'] (symbolize-term t vmap)]
                                     [(conj acc t') vmap']))
                                 [[] var-map]
                                 term)]
       ;; Keep as vector (works for both lists and vectors in pattern matching)
       [result vmap])
     
     (map? term)
     (reduce (fn [[acc vmap] [k v]]
               (let [[k' vmap'] (symbolize-term k vmap)
                     [v' vmap''] (symbolize-term v vmap')]
                 [(assoc acc k' v') vmap'']))
             [{} var-map]
             term)
     
     :else [term var-map])))

(defn vars-in-term
  "Extract all logic variables from a term."
  [term]
  (cond
    (lvar? term) #{term}
    (sequential? term) (reduce into #{} (map vars-in-term term))
    (map? term) (reduce into #{} (map vars-in-term (concat (keys term) (vals term))))
    :else #{}))

