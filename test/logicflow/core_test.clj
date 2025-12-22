(ns logicflow.core-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [logicflow.core :as core]
            [logicflow.kb :as kb]))

;; ============================================================================
;; Test Fixtures
;; ============================================================================

(defn clean-kb-fixture [f]
  (kb/clear-kb!)
  (f)
  (kb/clear-kb!))

(use-fixtures :each clean-kb-fixture)

;; ============================================================================
;; Fact Definition Tests
;; ============================================================================

(deftest deffact-test
  (testing "Defining facts with vector syntax"
    (core/deffact parent [:tom :mary])
    (is (contains? (kb/get-facts :parent) [:tom :mary])))
  
  (testing "Defining facts with multiple args"
    (core/deffact likes :john :pizza)
    (is (contains? (kb/get-facts :likes) [:john :pizza]))))

(deftest facts-macro-test
  (testing "Defining multiple facts at once"
    (core/facts
     (parent :tom :mary)
     (parent :tom :bob)
     (parent :mary :ann))
    (is (= 3 (count (kb/get-facts :parent))))))

;; ============================================================================
;; Rule Definition Tests
;; ============================================================================

(deftest defrule-test
  (testing "Defining rules with explicit head"
    (core/defrule grandparent [?x ?z]
      (parent ?x ?y)
      (parent ?y ?z))
    (is (= 1 (count (kb/get-rules :grandparent))))))

(deftest arrow-rule-test
  (testing "Defining rules with <- syntax"
    (core/<- (ancestor ?x ?y)
             (parent ?x ?y))
    (is (= 1 (count (kb/get-rules :ancestor))))))

;; ============================================================================
;; Query Tests
;; ============================================================================

(deftest simple-query-test
  (testing "Simple fact query"
    (core/facts
     (parent :tom :mary)
     (parent :tom :bob)
     (parent :mary :ann))
    
    (let [results (core/query (parent ?x :mary))]
      (is (= 1 (count results)))
      (is (= :tom (:x (first results)))))))

(deftest query-all-test
  (testing "Query returning multiple results"
    (core/facts
     (parent :tom :mary)
     (parent :tom :bob)
     (parent :mary :ann))
    
    (let [results (core/query (parent :tom ?child))]
      (is (= 2 (count results)))
      (is (= #{:mary :bob} (set (map :child results)))))))

(deftest rule-query-test
  (testing "Query using rules"
    (core/facts
     (parent :tom :mary)
     (parent :mary :ann))
    
    (core/<- (grandparent ?x ?z)
             (parent ?x ?y)
             (parent ?y ?z))
    
    (let [results (core/query (grandparent :tom ?who))]
      (is (= 1 (count results)))
      (is (= :ann (:who (first results)))))))

(deftest recursive-rule-test
  (testing "Recursive rule queries"
    (core/facts
     (parent :tom :mary)
     (parent :mary :ann)
     (parent :ann :pat))
    
    (core/<- (ancestor ?x ?y)
             (parent ?x ?y))
    
    (core/<- (ancestor ?x ?z)
             (parent ?x ?y)
             (ancestor ?y ?z))
    
    (let [results (core/query (ancestor :tom ?who))]
      (is (= 3 (count results)))
      (is (= #{:mary :ann :pat} (set (map :who results)))))))

;; ============================================================================
;; REPL Helper Tests
;; ============================================================================

(deftest show-facts-test
  (testing "show-facts returns facts"
    (core/facts
     (parent :tom :mary)
     (likes :john :pizza))
    
    (let [facts (core/show-facts)]
      (is (contains? facts :parent))
      (is (contains? facts :likes)))))

(deftest show-rules-test
  (testing "show-rules returns rules"
    (core/<- (grandparent ?x ?z)
             (parent ?x ?y)
             (parent ?y ?z))
    
    (let [rules (core/show-rules)]
      (is (contains? rules :grandparent)))))

;; ============================================================================
;; Example Loading Tests
;; ============================================================================

(deftest load-family-example-test
  (testing "Loading family example"
    (core/load-family-example!)
    
    (is (seq (kb/get-facts :parent)))
    (is (seq (kb/get-facts :male)))
    (is (seq (kb/get-facts :female)))
    (is (seq (kb/get-rules :ancestor)))
    (is (seq (kb/get-rules :grandparent)))))

(deftest load-animal-example-test
  (testing "Loading animal example"
    (core/load-animal-example!)
    
    (is (seq (kb/get-facts :has-feathers)))
    (is (seq (kb/get-rules :bird)))
    (is (seq (kb/get-rules :mammal)))))

