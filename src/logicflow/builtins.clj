(ns logicflow.builtins
  "Built-in predicates for Prolog-like operations.
   Includes arithmetic, comparisons, list operations, and control flow."
  (:require [logicflow.unify :as u]
            [logicflow.search :as s]))

;; ============================================================================
;; Arithmetic Expression Evaluation
;; ============================================================================

(defn eval-arith
  "Evaluate an arithmetic expression with substitutions applied."
  [expr subs]
  (let [e (u/walk* expr subs)]
    (cond
      (number? e) e
      
      (u/lvar? e) nil  ;; Unbound variable
      
      (and (sequential? e) (seq e))
      (let [[op & args] e
            vals (map #(eval-arith % subs) args)]
        (when (every? number? vals)
          (case op
            + (apply + vals)
            - (apply - vals)
            * (apply * vals)
            / (if (some zero? (rest vals))
                nil
                (apply / vals))
            mod (apply mod vals)
            rem (apply rem vals)
            abs (Math/abs (first vals))
            min (apply min vals)
            max (apply max vals)
            pow (Math/pow (first vals) (second vals))
            sqrt (Math/sqrt (first vals))
            floor (Math/floor (first vals))
            ceil (Math/ceil (first vals))
            round (Math/round (double (first vals)))
            nil)))
      
      :else nil)))

(defn is-goal
  "X is Expr - evaluate Expr and unify with X (like Prolog's is/2)."
  [x expr]
  (fn [subs]
    (let [result (eval-arith expr subs)]
      (if result
        ((u/== x result) subs)
        []))))

;; ============================================================================
;; Comparison Predicates
;; ============================================================================

(defn gt
  "X > Y - arithmetic greater than."
  [x y]
  (fn [subs]
    (let [x' (u/walk x subs)
          y' (u/walk y subs)]
      (if (and (number? x') (number? y') (> x' y'))
        [subs]
        []))))

(defn lt
  "X < Y - arithmetic less than."
  [x y]
  (fn [subs]
    (let [x' (u/walk x subs)
          y' (u/walk y subs)]
      (if (and (number? x') (number? y') (< x' y'))
        [subs]
        []))))

(defn gte
  "X >= Y - arithmetic greater than or equal."
  [x y]
  (fn [subs]
    (let [x' (u/walk x subs)
          y' (u/walk y subs)]
      (if (and (number? x') (number? y') (>= x' y'))
        [subs]
        []))))

(defn lte
  "X =< Y - arithmetic less than or equal."
  [x y]
  (fn [subs]
    (let [x' (u/walk x subs)
          y' (u/walk y subs)]
      (if (and (number? x') (number? y') (<= x' y'))
        [subs]
        []))))

(defn eq
  "X =:= Y - arithmetic equality."
  [x y]
  (fn [subs]
    (let [x' (eval-arith x subs)
          y' (eval-arith y subs)]
      (if (and x' y' (== x' y'))
        [subs]
        []))))

(defn neq
  "X =\\= Y - arithmetic inequality."
  [x y]
  (fn [subs]
    (let [x' (eval-arith x subs)
          y' (eval-arith y subs)]
      (if (and x' y' (not= x' y'))
        [subs]
        []))))

(defn term-eq
  "X == Y - term equality (no unification)."
  [x y]
  (fn [subs]
    (let [x' (u/walk* x subs)
          y' (u/walk* y subs)]
      (if (= x' y')
        [subs]
        []))))

(defn term-neq
  "X \\== Y - term inequality."
  [x y]
  (fn [subs]
    (let [x' (u/walk* x subs)
          y' (u/walk* y subs)]
      (if (not= x' y')
        [subs]
        []))))

;; ============================================================================
;; List Operations
;; ============================================================================

(defn membero
  "member(X, List) - X is a member of List."
  [x lst]
  (fn [subs]
    (let [l (u/walk lst subs)]
      (cond
        (u/lvar? l) []  ;; Can't enumerate unbound list
        (empty? l) []
        (sequential? l)
        (lazy-cat
         ((u/== x (first l)) subs)
         ((membero x (rest l)) subs))
        :else []))))

(defn appendo
  "append(L1, L2, L3) - L3 is concatenation of L1 and L2."
  [l1 l2 l3]
  (fn [subs]
    (lazy-cat
     ;; Base case: L1 = [], L2 = L3
     ((s/conj-goals (u/== l1 []) (u/== l2 l3)) subs)
     ;; Recursive case: L1 = [H|T], L3 = [H|R], append(T, L2, R)
     (let [h (u/lvar "h")
           t (u/lvar "t")
           r (u/lvar "r")]
       ((s/conj-all
         (u/== l1 (list h '& t))  ;; Pattern: [H | T]
         (u/== l3 (list h '& r))
         (fn [s]
           (let [l1' (u/walk l1 s)
                 l3' (u/walk l3 s)]
             (if (and (sequential? l1') (seq l1')
                      (sequential? l3') (seq l3'))
               ((s/conj-all
                 (u/== h (first l1'))
                 (u/== t (vec (rest l1')))
                 (u/== (first l3') (first l1'))
                 (u/== r (vec (rest l3')))
                 (appendo t l2 r)) s)
               []))))
        subs)))))

(defn lengtho
  "length(List, N) - N is the length of List."
  [lst n]
  (fn [subs]
    (let [l (u/walk lst subs)
          n' (u/walk n subs)]
      (cond
        ;; If list is ground, compute length
        (and (sequential? l) (not (u/lvar? (first l))))
        ((u/== n (count l)) subs)
        
        ;; If n is ground, generate list of that length
        (number? n')
        (if (neg? n')
          []
          ((u/== lst (vec (repeatedly n' #(u/lvar "x")))) subs))
        
        :else []))))

(defn ntho
  "nth(N, List, X) - X is the Nth element of List (0-indexed)."
  [n lst x]
  (fn [subs]
    (let [l (u/walk lst subs)
          n' (u/walk n subs)]
      (if (and (sequential? l) (number? n') (>= n' 0) (< n' (count l)))
        ((u/== x (nth l n')) subs)
        []))))

(defn reverseo
  "reverse(List, Reversed) - Reversed is List reversed."
  [lst reversed]
  (fn [subs]
    (let [l (u/walk lst subs)]
      (if (sequential? l)
        ((u/== reversed (vec (reverse l))) subs)
        []))))

(defn lasto
  "last(List, X) - X is the last element of List."
  [lst x]
  (fn [subs]
    (let [l (u/walk lst subs)]
      (if (and (sequential? l) (seq l))
        ((u/== x (last l)) subs)
        []))))

(defn firsto
  "first(List, X) - X is the first element of List (head)."
  [lst x]
  (fn [subs]
    (let [l (u/walk lst subs)]
      (if (and (sequential? l) (seq l))
        ((u/== x (first l)) subs)
        []))))

(defn resto
  "rest(List, Tail) - Tail is List without first element."
  [lst tail]
  (fn [subs]
    (let [l (u/walk lst subs)]
      (if (and (sequential? l) (seq l))
        ((u/== tail (vec (rest l))) subs)
        []))))

(defn conso
  "cons(Head, Tail, List) - List = [Head | Tail]."
  [head tail lst]
  (fn [subs]
    (let [h (u/walk head subs)
          t (u/walk tail subs)
          l (u/walk lst subs)]
      (cond
        ;; Build list from head and tail
        (and (not (u/lvar? h)) (sequential? t))
        ((u/== lst (vec (cons h t))) subs)
        
        ;; Decompose list
        (and (sequential? l) (seq l))
        ((s/conj-goals (u/== head (first l))
                       (u/== tail (vec (rest l)))) subs)
        
        :else []))))

(defn emptyo
  "empty(List) - List is empty."
  [lst]
  (fn [subs]
    (let [l (u/walk lst subs)]
      (if (and (sequential? l) (empty? l))
        [subs]
        []))))

(defn non-emptyo
  "non_empty(List) - List is not empty."
  [lst]
  (fn [subs]
    (let [l (u/walk lst subs)]
      (if (and (sequential? l) (seq l))
        [subs]
        []))))

;; ============================================================================
;; Type Checking Predicates
;; ============================================================================

(defn numbero
  "number(X) - X is a number."
  [x]
  (fn [subs]
    (if (number? (u/walk x subs))
      [subs]
      [])))

(defn integero
  "integer(X) - X is an integer."
  [x]
  (fn [subs]
    (if (integer? (u/walk x subs))
      [subs]
      [])))

(defn atomo
  "atom(X) - X is an atom (keyword or symbol)."
  [x]
  (fn [subs]
    (let [v (u/walk x subs)]
      (if (or (keyword? v) (symbol? v))
        [subs]
        []))))

(defn listo
  "is_list(X) - X is a list."
  [x]
  (fn [subs]
    (if (sequential? (u/walk x subs))
      [subs]
      [])))

(defn varo
  "var(X) - X is an unbound variable."
  [x]
  (fn [subs]
    (if (u/lvar? (u/walk x subs))
      [subs]
      [])))

(defn nonvaro
  "nonvar(X) - X is bound."
  [x]
  (fn [subs]
    (if (not (u/lvar? (u/walk x subs)))
      [subs]
      [])))

(defn groundo
  "ground(X) - X contains no unbound variables."
  [x]
  (fn [subs]
    (let [v (u/walk* x subs)]
      (if (empty? (u/vars-in-term v))
        [subs]
        []))))

;; ============================================================================
;; Control Flow
;; ============================================================================

(defn trueo
  "true - always succeeds."
  []
  (fn [subs] [subs]))

(defn failo
  "fail - always fails."
  []
  (fn [_subs] []))

(defn onceo
  "once(Goal) - succeed at most once."
  [goal]
  (fn [subs]
    (take 1 (goal subs))))

(defn repeato
  "repeat - infinite choice point."
  []
  (fn [subs]
    (lazy-seq (cons subs ((repeato) subs)))))

(defn ifo
  "if(Cond, Then, Else) - conditional."
  [cond-goal then-goal else-goal]
  (fn [subs]
    (let [cond-results (cond-goal subs)]
      (if (seq cond-results)
        (mapcat then-goal cond-results)
        (else-goal subs)))))

;; ============================================================================
;; String Operations
;; ============================================================================

(defn atom-stringoo
  "atom_string(Atom, String) - convert between atom and string."
  [atom string]
  (fn [subs]
    (let [a (u/walk atom subs)
          s (u/walk string subs)]
      (cond
        (keyword? a) ((u/== string (name a)) subs)
        (string? s) ((u/== atom (keyword s)) subs)
        :else []))))

(defn atom-concato
  "atom_concat(A1, A2, A3) - A3 is concatenation of atoms A1 and A2."
  [a1 a2 a3]
  (fn [subs]
    (let [v1 (u/walk a1 subs)
          v2 (u/walk a2 subs)
          v3 (u/walk a3 subs)]
      (cond
        (and (keyword? v1) (keyword? v2))
        ((u/== a3 (keyword (str (name v1) (name v2)))) subs)
        
        :else []))))

;; ============================================================================
;; Findall / Bagof / Setof
;; ============================================================================

(defn findall
  "findall(Template, Goal, List) - collect all solutions."
  [template goal]
  (fn [subs]
    (let [results (goal subs)
          collected (mapv #(u/walk* template %) results)]
      [[(u/lvar "results") collected]])))

(defn findall-goal
  "findall(Template, Goal, List) - as a goal."
  [template goal list-var]
  (fn [subs]
    (let [results (goal subs)
          collected (vec (map #(u/walk* template %) results))]
      ((u/== list-var collected) subs))))

;; ============================================================================
;; Utility
;; ============================================================================

(defn copy-termo
  "copy_term(Term, Copy) - Copy is a copy of Term with fresh variables."
  [term copy]
  (fn [subs]
    (let [t (u/walk* term subs)
          [t' _] (u/symbolize-term t)]
      ((u/== copy t') subs))))

(defn between
  "between(Low, High, X) - X is between Low and High inclusive."
  [low high x]
  (fn [subs]
    (let [l (u/walk low subs)
          h (u/walk high subs)
          v (u/walk x subs)]
      (cond
        ;; X is bound - check if in range
        (number? v)
        (if (and (number? l) (number? h) (<= l v h))
          [subs]
          [])
        
        ;; Generate values
        (and (number? l) (number? h) (u/lvar? v))
        (map (fn [n] (assoc subs v n))
             (range l (inc h)))
        
        :else []))))

(defn succ
  "succ(X, S) - S is X + 1."
  [x s]
  (fn [subs]
    (let [x' (u/walk x subs)
          s' (u/walk s subs)]
      (cond
        (number? x') ((u/== s (inc x')) subs)
        (number? s') ((u/== x (dec s')) subs)
        :else []))))

(defn plus
  "plus(X, Y, Z) - Z = X + Y, works in multiple directions."
  [x y z]
  (fn [subs]
    (let [x' (u/walk x subs)
          y' (u/walk y subs)
          z' (u/walk z subs)]
      (cond
        (and (number? x') (number? y')) ((u/== z (+ x' y')) subs)
        (and (number? x') (number? z')) ((u/== y (- z' x')) subs)
        (and (number? y') (number? z')) ((u/== x (- z' y')) subs)
        :else []))))

(defn times
  "times(X, Y, Z) - Z = X * Y, works in multiple directions."
  [x y z]
  (fn [subs]
    (let [x' (u/walk x subs)
          y' (u/walk y subs)
          z' (u/walk z subs)]
      (cond
        (and (number? x') (number? y')) ((u/== z (* x' y')) subs)
        (and (number? x') (number? z') (not (zero? x'))) ((u/== y (/ z' x')) subs)
        (and (number? y') (number? z') (not (zero? y'))) ((u/== x (/ z' y')) subs)
        :else []))))

