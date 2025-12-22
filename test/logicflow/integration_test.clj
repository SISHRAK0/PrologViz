(ns logicflow.integration-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [logicflow.core :as core]
            [logicflow.kb :as kb]
            [logicflow.unify :as u]
            [logicflow.search :as s]))

;; ============================================================================
;; Test Fixtures
;; ============================================================================

(defn clean-kb-fixture [f]
  (kb/clear-kb!)
  (f)
  (kb/clear-kb!))

(use-fixtures :each clean-kb-fixture)

;; ============================================================================
;; Full Integration Tests
;; ============================================================================

(deftest family-relations-test
  (testing "Complete family relations scenario"
    ;; Setup
    (core/facts
     (parent :tom :mary)
     (parent :tom :bob)
     (parent :mary :ann)
     (parent :mary :pat)
     (parent :bob :jim)
     (parent :bob :liz)
     
     (male :tom)
     (male :bob)
     (male :jim)
     (male :pat)
     (female :mary)
     (female :ann)
     (female :liz))
    
    ;; Define rules
    (core/<- (father ?x ?y)
             (parent ?x ?y)
             (male ?x))
    
    (core/<- (mother ?x ?y)
             (parent ?x ?y)
             (female ?x))
    
    (core/<- (grandparent ?x ?z)
             (parent ?x ?y)
             (parent ?y ?z))
    
    (core/<- (ancestor ?x ?y)
             (parent ?x ?y))
    
    (core/<- (ancestor ?x ?z)
             (parent ?x ?y)
             (ancestor ?y ?z))
    
    ;; Test queries
    (testing "Father query"
      (let [results (core/query (father :tom ?child))]
        (is (= 2 (count results)))
        (is (= #{:mary :bob} (set (map :child results))))))
    
    (testing "Mother query"
      (let [results (core/query (mother :mary ?child))]
        (is (= 2 (count results)))
        (is (= #{:ann :pat} (set (map :child results))))))
    
    (testing "Grandparent query"
      (let [results (core/query (grandparent :tom ?grandchild))]
        (is (= 4 (count results)))
        (is (= #{:ann :pat :jim :liz} (set (map :grandchild results))))))
    
    (testing "Ancestor query (recursive)"
      (let [results (core/query (ancestor :tom ?descendant))]
        (is (= 6 (count results)))
        (is (= #{:mary :bob :ann :pat :jim :liz} 
               (set (map :descendant results))))))))

(deftest animal-classification-test
  (testing "Animal classification scenario"
    ;; Setup
    (core/facts
     (has-feathers :tweety)
     (has-feathers :penguin)
     (has-fur :fido)
     (has-fur :whiskers)
     (gives-milk :bessie)
     (gives-milk :whiskers)
     (can-fly :tweety)
     (swims :penguin)
     (swims :fido))
    
    ;; Define rules
    (core/<- (bird ?x)
             (has-feathers ?x))
    
    (core/<- (mammal ?x)
             (gives-milk ?x))
    
    (core/<- (mammal ?x)
             (has-fur ?x))
    
    (core/<- (flying-bird ?x)
             (bird ?x)
             (can-fly ?x))
    
    (core/<- (swimming-animal ?x)
             (swims ?x))
    
    ;; Test queries
    (testing "Bird classification"
      (let [results (core/query (bird ?who))]
        (is (= 2 (count results)))
        (is (= #{:tweety :penguin} (set (map :who results))))))
    
    (testing "Mammal classification"
      (let [results (core/query (mammal ?who))]
        ;; whiskers matches both has-fur and gives-milk
        (is (>= (count results) 3))))
    
    (testing "Flying bird classification"
      (let [results (core/query (flying-bird ?who))]
        (is (= 1 (count results)))
        (is (= :tweety (:who (first results))))))))

(deftest stm-concurrent-test
  (testing "STM concurrent modifications"
    (let [results (atom [])]
      ;; Concurrent assertions
      (doall
       (pmap
        (fn [i]
          (kb/assert-fact! :number [(keyword (str "n" i))]))
        (range 100)))
      
      ;; Verify all facts are present
      (is (= 100 (count (kb/get-facts :number)))))))

(deftest history-tracking-test
  (testing "Transaction history is tracked"
    (kb/assert-fact! :test [:a :b])
    (kb/assert-fact! :test [:c :d])
    (kb/retract-fact! :test [:a :b])
    
    (let [history (kb/get-history)]
      (is (>= (count history) 3))
      (is (some #(= :assert (:type %)) history))
      (is (some #(= :retract (:type %)) history)))))

(deftest export-import-test
  (testing "Knowledge base export and import"
    ;; Setup some data
    (core/facts
     (parent :tom :mary)
     (parent :mary :ann))
    
    (core/<- (ancestor ?x ?y)
             (parent ?x ?y))
    
    ;; Export
    (let [exported (kb/export-kb)]
      (is (contains? exported :facts))
      (is (contains? exported :rules))
      
      ;; Clear and import
      (kb/clear-kb!)
      (is (empty? (kb/get-all-facts)))
      
      (kb/import-kb! exported)
      
      ;; Verify imported data
      (is (contains? (kb/get-facts :parent) [:tom :mary]))
      (is (seq (kb/get-rules :ancestor))))))

(deftest query-caching-test
  (testing "Query cache invalidation"
    (core/facts
     (number :one)
     (number :two))
    
    ;; First query
    (let [results1 (core/query (number ?x))]
      (is (= 2 (count results1)))
      
      ;; Add more facts
      (core/deffact number :three)
      
      ;; Query again - should include new fact
      (let [results2 (core/query (number ?x))]
        (is (= 3 (count results2)))))))

;; ============================================================================
;; Edge Cases
;; ============================================================================

(deftest empty-kb-query-test
  (testing "Querying empty knowledge base"
    (let [results (core/query (nonexistent ?x))]
      (is (empty? results)))))

(deftest duplicate-facts-test
  (testing "Duplicate facts are handled"
    (core/deffact test-pred [:a :b])
    (core/deffact test-pred [:a :b])
    
    ;; Sets should prevent duplicates
    (is (= 1 (count (kb/get-facts :test-pred))))))

(deftest complex-unification-test
  (testing "Complex nested unification"
    (core/facts
     (person {:name "Tom" :age 40})
     (person {:name "Mary" :age 35}))
    
    (let [results (core/query (person {:name ?name :age ?age}))]
      (is (= 2 (count results)))
      (is (some #(= "Tom" (:name %)) results))
      (is (some #(= "Mary" (:name %)) results)))))

