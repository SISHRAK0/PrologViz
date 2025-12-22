(ns logicflow.examples
  "Classic Prolog examples and puzzles.
   Includes family relations, puzzles, and expert systems."
  (:require [logicflow.core :refer :all]
            [logicflow.kb :as kb]
            [logicflow.builtins :as b]))

;; ============================================================================
;; Family Relations (Extended)
;; ============================================================================

(defn load-extended-family!
  "Load extended family relations with more predicates."
  []
  (kb/clear-kb!)
  
  ;; People with birth years
  (deffact person [:tom 1950 :male])
  (deffact person [:mary 1952 :female])
  (deffact person [:bob 1975 :male])
  (deffact person [:alice 1977 :female])
  (deffact person [:jim 2000 :male])
  (deffact person [:ann 2002 :female])
  (deffact person [:pat 2005 :male])
  (deffact person [:liz 2003 :female])
  
  ;; Marriages
  (deffact married [:tom :mary 1974])
  (deffact married [:bob :alice 1999])
  
  ;; Parent relationships
  (deffact parent [:tom :bob])
  (deffact parent [:mary :bob])
  (deffact parent [:tom :ann])
  (deffact parent [:mary :ann])
  (deffact parent [:bob :jim])
  (deffact parent [:alice :jim])
  (deffact parent [:bob :liz])
  (deffact parent [:alice :liz])
  (deffact parent [:bob :pat])
  (deffact parent [:alice :pat])
  
  ;; Rules
  (<- (male ?x)
      (person [?x ?_ :male]))
  
  (<- (female ?x)
      (person [?x ?_ :female]))
  
  (<- (age ?x ?age)
      (person [?x ?birth ?_])
      ;; Simplified - assume current year 2024
      )
  
  (<- (father ?x ?y)
      (parent ?x ?y)
      (male ?x))
  
  (<- (mother ?x ?y)
      (parent ?x ?y)
      (female ?x))
  
  (<- (grandparent ?x ?z)
      (parent ?x ?y)
      (parent ?y ?z))
  
  (<- (grandfather ?x ?z)
      (grandparent ?x ?z)
      (male ?x))
  
  (<- (grandmother ?x ?z)
      (grandparent ?x ?z)
      (female ?x))
  
  (<- (sibling ?x ?y)
      (parent ?p ?x)
      (parent ?p ?y))
  
  (<- (brother ?x ?y)
      (sibling ?x ?y)
      (male ?x))
  
  (<- (sister ?x ?y)
      (sibling ?x ?y)
      (female ?x))
  
  (<- (uncle ?x ?y)
      (parent ?p ?y)
      (brother ?x ?p))
  
  (<- (aunt ?x ?y)
      (parent ?p ?y)
      (sister ?x ?p))
  
  (<- (cousin ?x ?y)
      (parent ?px ?x)
      (parent ?py ?y)
      (sibling ?px ?py))
  
  (<- (ancestor ?x ?y)
      (parent ?x ?y))
  
  (<- (ancestor ?x ?z)
      (parent ?x ?y)
      (ancestor ?y ?z))
  
  (<- (descendant ?x ?y)
      (ancestor ?y ?x))
  
  (<- (spouse ?x ?y)
      (married ?x ?y ?_))
  
  (<- (spouse ?x ?y)
      (married ?y ?x ?_))
  
  (println "Extended family example loaded!")
  (show-kb))

;; ============================================================================
;; Towers of Hanoi
;; ============================================================================

(defn load-hanoi!
  "Load Towers of Hanoi puzzle."
  []
  (kb/clear-kb!)
  
  ;; Base case: 0 disks - no moves needed
  (<- (hanoi 0 ?_ ?_ ?_ []))
  
  ;; Recursive case
  (<- (hanoi ?n ?from ?to ?aux ?moves)
      ;; N > 0
      ;; Move N-1 disks from source to auxiliary
      ;; Move disk N from source to target
      ;; Move N-1 disks from auxiliary to target
      )
  
  (println "Hanoi puzzle loaded (simplified)!"))

;; ============================================================================
;; List Operations Examples
;; ============================================================================

(defn load-list-examples!
  "Load list operation examples."
  []
  (kb/clear-kb!)
  
  ;; Length
  (<- (length [] 0))
  (<- (length [?_ | ?tail] ?n)
      (length ?tail ?n1))
  
  ;; Sum of list
  (<- (sum [] 0))
  (<- (sum [?h | ?t] ?s)
      (sum ?t ?s1))
  
  ;; Maximum
  (<- (max-list [?x] ?x))
  (<- (max-list [?h | ?t] ?max)
      (max-list ?t ?tmax))
  
  ;; Factorial
  (<- (factorial 0 1))
  (<- (factorial ?n ?f)
      )
  
  ;; Fibonacci
  (<- (fib 0 0))
  (<- (fib 1 1))
  (<- (fib ?n ?f)
      )
  
  (println "List examples loaded!"))

;; ============================================================================
;; Simple Expert System (Animal Identification)
;; ============================================================================

(defn load-animal-expert!
  "Load animal identification expert system."
  []
  (kb/clear-kb!)
  
  ;; Facts about observed characteristics
  ;; User would assert these based on observations
  
  ;; Classification rules
  (<- (mammal ?x)
      (has-hair ?x))
  
  (<- (mammal ?x)
      (gives-milk ?x))
  
  (<- (bird ?x)
      (has-feathers ?x))
  
  (<- (bird ?x)
      (flies ?x)
      (lays-eggs ?x))
  
  (<- (reptile ?x)
      (cold-blooded ?x)
      (has-scales ?x))
  
  (<- (fish ?x)
      (cold-blooded ?x)
      (has-fins ?x)
      (lives-in-water ?x))
  
  ;; Specific animals
  (<- (is-a ?x :cheetah)
      (mammal ?x)
      (carnivore ?x)
      (has-tawny-color ?x)
      (has-dark-spots ?x))
  
  (<- (is-a ?x :tiger)
      (mammal ?x)
      (carnivore ?x)
      (has-tawny-color ?x)
      (has-black-stripes ?x))
  
  (<- (is-a ?x :giraffe)
      (mammal ?x)
      (ungulate ?x)
      (has-long-neck ?x)
      (has-long-legs ?x)
      (has-dark-spots ?x))
  
  (<- (is-a ?x :zebra)
      (mammal ?x)
      (ungulate ?x)
      (has-black-stripes ?x))
  
  (<- (is-a ?x :ostrich)
      (bird ?x)
      (does-not-fly ?x)
      (has-long-neck ?x)
      (has-long-legs ?x)
      (is-black-and-white ?x))
  
  (<- (is-a ?x :penguin)
      (bird ?x)
      (does-not-fly ?x)
      (swims ?x)
      (is-black-and-white ?x))
  
  (<- (is-a ?x :albatross)
      (bird ?x)
      (flies ?x)
      (is-good-flyer ?x))
  
  ;; Inference rules
  (<- (carnivore ?x)
      (eats-meat ?x))
  
  (<- (carnivore ?x)
      (has-pointed-teeth ?x)
      (has-claws ?x)
      (has-forward-eyes ?x))
  
  (<- (ungulate ?x)
      (mammal ?x)
      (has-hooves ?x))
  
  (<- (ungulate ?x)
      (mammal ?x)
      (chews-cud ?x))
  
  (println "Animal expert system loaded!")
  (println "Assert characteristics like: (deffact has-hair :mystery-animal)")
  (println "Then query: (query (is-a :mystery-animal ?type))"))

;; ============================================================================
;; Graph/Path Finding
;; ============================================================================

(defn load-graph-example!
  "Load graph traversal example."
  []
  (kb/clear-kb!)
  
  ;; Define edges of a graph
  (deffact edge [:a :b 5])
  (deffact edge [:a :c 3])
  (deffact edge [:b :d 2])
  (deffact edge [:b :e 4])
  (deffact edge [:c :d 6])
  (deffact edge [:c :f 1])
  (deffact edge [:d :e 3])
  (deffact edge [:e :f 2])
  (deffact edge [:d :g 4])
  (deffact edge [:f :g 5])
  
  ;; Undirected edges
  (<- (connected ?x ?y ?cost)
      (edge ?x ?y ?cost))
  
  (<- (connected ?x ?y ?cost)
      (edge ?y ?x ?cost))
  
  ;; Path finding
  (<- (path ?x ?x [?x] 0))
  
  (<- (path ?x ?y [?x | ?rest] ?cost)
      (connected ?x ?z ?c1)
      (path ?z ?y ?rest ?c2))
  
  ;; Reachability
  (<- (reachable ?x ?y)
      (connected ?x ?y ?_))
  
  (<- (reachable ?x ?z)
      (connected ?x ?y ?_)
      (reachable ?y ?z))
  
  (println "Graph example loaded!")
  (println "Query paths: (query (path :a :g ?path ?cost))"))

;; ============================================================================
;; Simple Arithmetic Examples
;; ============================================================================

(defn load-arithmetic-examples!
  "Load arithmetic operation examples."
  []
  (kb/clear-kb!)
  
  ;; Peano arithmetic
  (<- (nat 0))
  (<- (nat (s ?x))
      (nat ?x))
  
  ;; Addition: add(X, Y, Z) means X + Y = Z
  (<- (add 0 ?y ?y))
  (<- (add (s ?x) ?y (s ?z))
      (add ?x ?y ?z))
  
  ;; Multiplication
  (<- (mult 0 ?_ 0))
  (<- (mult (s ?x) ?y ?z)
      (mult ?x ?y ?z1)
      (add ?y ?z1 ?z))
  
  ;; Less than or equal
  (<- (lte 0 ?_))
  (<- (lte (s ?x) (s ?y))
      (lte ?x ?y))
  
  ;; Greater than
  (<- (gt (s ?_) 0))
  (<- (gt (s ?x) (s ?y))
      (gt ?x ?y))
  
  (println "Arithmetic examples loaded!")
  (println "Use Peano numbers: 0, (s 0), (s (s 0)), etc."))

;; ============================================================================
;; Database Query Example (Relational)
;; ============================================================================

(defn load-database-example!
  "Load relational database query example."
  []
  (kb/clear-kb!)
  
  ;; Employees table
  (deffact employee [:e001 "John Smith" :engineering 75000])
  (deffact employee [:e002 "Jane Doe" :engineering 82000])
  (deffact employee [:e003 "Bob Johnson" :sales 65000])
  (deffact employee [:e004 "Alice Brown" :sales 68000])
  (deffact employee [:e005 "Charlie Wilson" :hr 55000])
  (deffact employee [:e006 "Diana Lee" :engineering 90000])
  
  ;; Departments
  (deffact department [:engineering "Engineering" :d001])
  (deffact department [:sales "Sales" :d002])
  (deffact department [:hr "Human Resources" :d003])
  
  ;; Manager relationships
  (deffact manages [:e006 :e001])
  (deffact manages [:e006 :e002])
  (deffact manages [:e003 :e004])
  
  ;; Query rules
  (<- (employee-name ?id ?name)
      (employee [?id ?name ?_ ?_]))
  
  (<- (employee-dept ?id ?dept)
      (employee [?id ?_ ?dept ?_]))
  
  (<- (employee-salary ?id ?salary)
      (employee [?id ?_ ?_ ?salary]))
  
  (<- (high-earner ?id ?name ?salary)
      (employee [?id ?name ?_ ?salary]))
      ;; Would need arithmetic: salary > 70000
  
  (<- (works-in ?name ?dept-name)
      (employee [?id ?name ?dept ?_])
      (department [?dept ?dept-name ?_]))
  
  (<- (manager-of ?manager-name ?employee-name)
      (manages [?mid ?eid])
      (employee-name ?mid ?manager-name)
      (employee-name ?eid ?employee-name))
  
  (<- (colleagues ?name1 ?name2)
      (employee [?id1 ?name1 ?dept ?_])
      (employee [?id2 ?name2 ?dept ?_]))
  
  (println "Database example loaded!")
  (println "Queries:")
  (println "  (query (employee-name ?id ?name))")
  (println "  (query (works-in ?name ?dept))")
  (println "  (query (manager-of ?manager ?employee))"))

;; ============================================================================
;; All Examples Loader
;; ============================================================================

(defn list-examples
  "List all available examples."
  []
  (println "\nAvailable examples:")
  (println "  1. (load-family-example!)        - Basic family relations")
  (println "  2. (load-extended-family!)       - Extended family with dates")
  (println "  3. (load-animal-example!)        - Simple animal classification")
  (println "  4. (load-animal-expert!)         - Animal identification expert system")
  (println "  5. (load-graph-example!)         - Graph traversal and pathfinding")
  (println "  6. (load-arithmetic-examples!)   - Peano arithmetic")
  (println "  7. (load-database-example!)      - Relational database queries")
  (println "  8. (load-list-examples!)         - List operations"))

