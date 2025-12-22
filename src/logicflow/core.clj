(ns logicflow.core
  "Core DSL for Prolog-like logic programming in Clojure.
   Provides macros for defining facts, rules, and queries."
  (:require [logicflow.unify :as u]
            [logicflow.search :as s]
            [logicflow.kb :as kb]))

;; ============================================================================
;; DSL Macros
;; ============================================================================

(defmacro deffact
  "Define a fact in the knowledge base.
   Usage: (deffact parent [:john :mary])
          (deffact likes :john :pizza)"
  [predicate & args]
  (let [pred-kw (if (keyword? predicate)
                  predicate
                  (keyword (name predicate)))
        fact-args (if (and (= 1 (count args))
                           (vector? (first args)))
                    (first args)
                    (vec args))]
    `(kb/assert-fact! ~pred-kw ~fact-args)))

(defmacro defrule
  "Define a rule in the knowledge base.
   Usage: (defrule grandparent [?x ?z]
            (parent ?x ?y)
            (parent ?y ?z))
   
   Or with :- syntax:
          (defrule (ancestor ?x ?y) :- (parent ?x ?y))"
  [name-or-head & body]
  (cond
    ;; Named rule with explicit head: (defrule name [?x ?y] (goal1) (goal2))
    (and (symbol? name-or-head)
         (not (empty? body))
         (vector? (first body)))
    (let [pred-kw (keyword (name name-or-head))
          head (first body)
          goals (rest body)]
      `(kb/add-rule! ~pred-kw '~head '~(vec goals)))
    
    ;; Prolog-style: (defrule (head ?x ?y) :- (goal1) (goal2))
    (and (list? name-or-head)
         (= :- (first body)))
    (let [[pred-name & args] name-or-head
          pred-kw (keyword (name pred-name))
          head (vec args)
          goals (rest body)]
      `(kb/add-rule! ~pred-kw '~head '~(vec goals)))
    
    ;; Simple form: (defrule name [args] body...)
    :else
    (let [pred-kw (keyword (name name-or-head))
          head (first body)
          goals (rest body)]
      `(kb/add-rule! ~pred-kw '~head '~(vec goals)))))

(defmacro <-
  "Alternative syntax for defrule.
   Usage: (<- (grandparent ?x ?z)
              (parent ?x ?y)
              (parent ?y ?z))"
  [head & body]
  (let [[pred-name & args] head
        pred-kw (keyword (name pred-name))]
    `(kb/add-rule! ~pred-kw '~(vec args) '~(vec body))))

(defmacro query
  "Execute a query against the knowledge base.
   Usage: (query (grandparent ?who :ann))
          (query (parent ?x ?y) (ancestor ?y ?z))"
  [& goals]
  `(kb/query '~(vec goals)))

(defmacro query-first
  "Execute a query and return only the first result."
  [& goals]
  `(first (kb/query '~(vec goals) :limit 1)))

(defmacro query-n
  "Execute a query and return at most n results."
  [n & goals]
  `(kb/query '~(vec goals) :limit ~n))

;; ============================================================================
;; Convenience Macros
;; ============================================================================

(defmacro facts
  "Define multiple facts at once.
   Usage: (facts
            (parent tom mary)
            (parent tom bob)
            (parent mary ann))"
  [& fact-forms]
  `(do
     ~@(for [form fact-forms]
         (let [[pred & args] form]
           `(deffact ~pred ~@args)))))

(defmacro rules
  "Define multiple rules at once.
   Usage: (rules
            (ancestor ?x ?y) :- (parent ?x ?y)
            (ancestor ?x ?z) :- (parent ?x ?y) (ancestor ?y ?z))"
  [& forms]
  (let [parse-rules (fn parse [forms acc]
                      (if (empty? forms)
                        acc
                        (let [head (first forms)
                              [sep & body-and-rest] (rest forms)]
                          (if (= :- sep)
                            ;; Find next head (a list not starting with keywords)
                            (let [[body remaining] (split-with
                                                    #(and (list? %)
                                                          (not (some #{:-} (rest %))))
                                                    body-and-rest)]
                              (recur remaining
                                     (conj acc {:head head :body body})))
                            (recur (rest forms) acc)))))
        parsed (parse-rules forms [])]
    `(do
       ~@(for [{:keys [head body]} parsed]
           (let [[pred-name & args] head]
             `(kb/add-rule! ~(keyword (name pred-name))
                            '~(vec args)
                            '~(vec body)))))))

(defmacro defrelation
  "Define a relation with facts and rules.
   Usage: (defrelation ancestor
            :facts [(tom mary) (tom bob)]
            :rules [([?x ?y] (parent ?x ?y))
                    ([?x ?z] (parent ?x ?y) (ancestor ?y ?z))])"
  [name & {:keys [facts rules]}]
  (let [pred-kw (keyword (name name))]
    `(do
       ~@(for [fact facts]
           `(kb/assert-fact! ~pred-kw ~(vec fact)))
       ~@(for [[head & body] rules]
           `(kb/add-rule! ~pred-kw '~head '~(vec body))))))

;; ============================================================================
;; Interactive Query DSL
;; ============================================================================

(defmacro ?-
  "Interactive query syntax (Prolog-like).
   Usage: (?- (grandparent ?x :ann))"
  [& goals]
  `(let [results# (kb/query '~(vec goals))]
     (if (empty? results#)
       (println "No solutions found.")
       (doseq [r# results#]
         (println r#)))
     results#))

(defmacro ?
  "Quick query returning first result.
   Usage: (? (parent ?x :mary))"
  [& goals]
  `(first (kb/query '~(vec goals) :limit 1)))

;; ============================================================================
;; Built-in Predicates
;; ============================================================================

(defn register-builtins!
  "Register built-in predicates in the knowledge base."
  []
  ;; Note: These would need special handling in the resolver
  ;; For now, they're documented for future implementation
  nil)

;; ============================================================================
;; REPL Helpers
;; ============================================================================

(defn show-facts
  "Display all facts in the knowledge base."
  ([] (show-facts nil))
  ([predicate]
   (let [facts (if predicate
                 {predicate (kb/get-facts predicate)}
                 (kb/get-all-facts))]
     (println "\n=== Facts ===")
     (doseq [[pred fact-set] (sort-by first facts)]
       (println (str pred ":"))
       (doseq [args fact-set]
         (println "  " args)))
     (println (str "\nTotal: " (reduce + (map count (vals facts))) " facts"))
     facts)))

(defn show-rules
  "Display all rules in the knowledge base."
  ([] (show-rules nil))
  ([predicate]
   (let [rules (if predicate
                 {predicate (kb/get-rules predicate)}
                 (kb/get-all-rules))]
     (println "\n=== Rules ===")
     (doseq [[pred rule-list] (sort-by first rules)]
       (println (str pred ":"))
       (doseq [{:keys [head body]} rule-list]
         (println "  " head " :- " (clojure.string/join ", " body))))
     (println (str "\nTotal: " (reduce + (map count (vals rules))) " rules"))
     rules)))

(defn show-kb
  "Display the entire knowledge base."
  []
  (show-facts)
  (show-rules)
  (println "\n=== Statistics ===")
  (let [stats (kb/get-stats)]
    (doseq [[k v] stats]
      (println " " k ":" v))))

(defn clear!
  "Clear the knowledge base."
  []
  (kb/clear-kb!)
  (println "Knowledge base cleared."))

;; ============================================================================
;; Example Knowledge Bases
;; ============================================================================

(defn load-family-example!
  "Load an example family relations knowledge base."
  []
  (clear!)
  ;; Facts
  (deffact parent :tom :mary)
  (deffact parent :tom :bob)
  (deffact parent :mary :ann)
  (deffact parent :mary :pat)
  (deffact parent :bob :jim)
  (deffact parent :bob :liz)
  
  (deffact male :tom)
  (deffact male :bob)
  (deffact male :jim)
  (deffact female :mary)
  (deffact female :ann)
  (deffact female :pat)
  (deffact female :liz)
  
  ;; Rules
  (<- (ancestor ?x ?y)
      (parent ?x ?y))
  
  (<- (ancestor ?x ?z)
      (parent ?x ?y)
      (ancestor ?y ?z))
  
  (<- (grandparent ?x ?z)
      (parent ?x ?y)
      (parent ?y ?z))
  
  (<- (sibling ?x ?y)
      (parent ?p ?x)
      (parent ?p ?y))
  
  (<- (father ?x ?y)
      (parent ?x ?y)
      (male ?x))
  
  (<- (mother ?x ?y)
      (parent ?x ?y)
      (female ?x))
  
  (println "Family example loaded!")
  (show-kb))

(defn load-animal-example!
  "Load an example animal classification knowledge base."
  []
  (clear!)
  ;; Facts about animals
  (deffact has-feathers :tweety)
  (deffact has-feathers :penguin)
  (deffact has-fur :fido)
  (deffact has-fur :cat)
  (deffact gives-milk :cow)
  (deffact gives-milk :cat)
  (deffact can-fly :tweety)
  (deffact lays-eggs :tweety)
  (deffact lays-eggs :penguin)
  
  ;; Classification rules
  (<- (bird ?x)
      (has-feathers ?x))
  
  (<- (mammal ?x)
      (gives-milk ?x))
  
  (<- (mammal ?x)
      (has-fur ?x))
  
  (<- (flying-bird ?x)
      (bird ?x)
      (can-fly ?x))
  
  (println "Animal example loaded!")
  (show-kb))

