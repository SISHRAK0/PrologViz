(ns logicflow.core
  "Core DSL for Prolog-like logic programming in Clojure."
  (:require [logicflow.unify :as u]
            [logicflow.search :as s]
            [logicflow.kb :as kb]))

(defmacro deffact [predicate & args]
  (let [pred-kw (if (keyword? predicate)
                  predicate
                  (keyword (name predicate)))
        fact-args (if (and (= 1 (count args))
                           (vector? (first args)))
                    (first args)
                    (vec args))]
    `(kb/assert-fact! ~pred-kw ~fact-args)))

(defmacro defrule [name-or-head & body]
  (cond
    (and (symbol? name-or-head)
         (not (empty? body))
         (vector? (first body)))
    (let [pred-kw (keyword (name name-or-head))
          head (first body)
          goals (rest body)]
      `(kb/add-rule! ~pred-kw '~head '~(vec goals)))
    
    (and (list? name-or-head)
         (= :- (first body)))
    (let [[pred-name & args] name-or-head
          pred-kw (keyword (name pred-name))
          head (vec args)
          goals (rest body)]
      `(kb/add-rule! ~pred-kw '~head '~(vec goals)))
    
    :else
    (let [pred-kw (keyword (name name-or-head))
          head (first body)
          goals (rest body)]
      `(kb/add-rule! ~pred-kw '~head '~(vec goals)))))

(defmacro <- [head & body]
  (let [[pred-name & args] head
        pred-kw (keyword (name pred-name))]
    `(kb/add-rule! ~pred-kw '~(vec args) '~(vec body))))

(defmacro query [& goals]
  `(kb/query '~(vec goals)))

(defmacro query-first [& goals]
  `(first (kb/query '~(vec goals) :limit 1)))

(defmacro query-n [n & goals]
  `(kb/query '~(vec goals) :limit ~n))

(defmacro facts [& fact-forms]
  `(do
     ~@(for [form fact-forms]
         (let [[pred & args] form]
           `(deffact ~pred ~@args)))))

(defmacro rules [& forms]
  (let [parse-rules (fn parse [forms acc]
                      (if (empty? forms)
                        acc
                        (let [head (first forms)
                              [sep & body-and-rest] (rest forms)]
                          (if (= :- sep)
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

(defmacro defrelation [name & {:keys [facts rules]}]
  (let [pred-kw (keyword (name name))]
    `(do
       ~@(for [fact facts]
           `(kb/assert-fact! ~pred-kw ~(vec fact)))
       ~@(for [[head & body] rules]
           `(kb/add-rule! ~pred-kw '~head '~(vec body))))))

(defmacro ?- [& goals]
  `(let [results# (kb/query '~(vec goals))]
     (if (empty? results#)
       (println "No solutions found.")
       (doseq [r# results#]
         (println r#)))
     results#))

(defmacro ? [& goals]
  `(first (kb/query '~(vec goals) :limit 1)))

(defn register-builtins! []
  nil)

(defn show-facts
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

(defn show-kb []
  (show-facts)
  (show-rules)
  (println "\n=== Statistics ===")
  (let [stats (kb/get-stats)]
    (doseq [[k v] stats]
      (println " " k ":" v))))

(defn clear! []
  (kb/clear-kb!)
  (println "Knowledge base cleared."))

(defn load-family-example! []
  (clear!)
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
  (<- (ancestor ?x ?y) (parent ?x ?y))
  (<- (ancestor ?x ?z) (parent ?x ?y) (ancestor ?y ?z))
  (<- (grandparent ?x ?z) (parent ?x ?y) (parent ?y ?z))
  (<- (sibling ?x ?y) (parent ?p ?x) (parent ?p ?y))
  (<- (father ?x ?y) (parent ?x ?y) (male ?x))
  (<- (mother ?x ?y) (parent ?x ?y) (female ?x))
  (println "Family example loaded!")
  (show-kb))

(defn load-animal-example! []
  (clear!)
  (deffact has-feathers :tweety)
  (deffact has-feathers :penguin)
  (deffact has-fur :fido)
  (deffact has-fur :cat)
  (deffact gives-milk :cow)
  (deffact gives-milk :cat)
  (deffact can-fly :tweety)
  (deffact lays-eggs :tweety)
  (deffact lays-eggs :penguin)
  (<- (bird ?x) (has-feathers ?x))
  (<- (mammal ?x) (gives-milk ?x))
  (<- (mammal ?x) (has-fur ?x))
  (<- (flying-bird ?x) (bird ?x) (can-fly ?x))
  (println "Animal example loaded!")
  (show-kb))
