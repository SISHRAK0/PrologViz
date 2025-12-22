(ns logicflow.unify-test
  (:require [clojure.test :refer [deftest testing is are]]
            [logicflow.unify :as u]))

;; ============================================================================
;; Logic Variable Tests
;; ============================================================================

(deftest lvar-test
  (testing "Creating logic variables"
    (let [x (u/lvar "x")
          y (u/lvar "y")]
      (is (u/lvar? x))
      (is (u/lvar? y))
      (is (not= x y))
      (is (= "x" (:name x)))
      (is (= "?x" (str x))))))

(deftest lvar-predicate-test
  (testing "lvar? predicate"
    (is (u/lvar? (u/lvar "x")))
    (is (not (u/lvar? 42)))
    (is (not (u/lvar? "hello")))
    (is (not (u/lvar? :keyword)))
    (is (not (u/lvar? [1 2 3])))))

;; ============================================================================
;; Walk Tests
;; ============================================================================

(deftest walk-test
  (testing "Walking through substitutions"
    (let [x (u/lvar "x")
          y (u/lvar "y")
          subs {x 1}]
      (is (= 1 (u/walk x subs)))
      (is (= y (u/walk y subs)))))
  
  (testing "Chained substitutions"
    (let [x (u/lvar "x")
          y (u/lvar "y")
          z (u/lvar "z")
          subs {x y, y z, z 42}]
      (is (= 42 (u/walk x subs)))
      (is (= 42 (u/walk y subs)))
      (is (= 42 (u/walk z subs))))))

(deftest walk*-test
  (testing "Deep walking through nested structures"
    (let [x (u/lvar "x")
          y (u/lvar "y")
          subs {x 1, y 2}]
      (is (= [1 2 3] (u/walk* [x y 3] subs)))
      (is (= {:a 1 :b 2} (u/walk* {:a x :b y} subs))))))

;; ============================================================================
;; Unification Tests
;; ============================================================================

(deftest unify-values-test
  (testing "Unifying equal values"
    (is (= {} (u/unify 1 1 {})))
    (is (= {} (u/unify :foo :foo {})))
    (is (= {} (u/unify "hello" "hello" {}))))
  
  (testing "Unifying different values fails"
    (is (nil? (u/unify 1 2 {})))
    (is (nil? (u/unify :foo :bar {})))
    (is (nil? (u/unify "hello" "world" {})))))

(deftest unify-lvar-test
  (testing "Unifying logic variable with value"
    (let [x (u/lvar "x")]
      (is (= {x 42} (u/unify x 42 {})))
      (is (= {x 42} (u/unify 42 x {})))))
  
  (testing "Unifying two logic variables"
    (let [x (u/lvar "x")
          y (u/lvar "y")]
      (let [subs (u/unify x y {})]
        (is subs)
        (is (= (u/walk x subs) (u/walk y subs)))))))

(deftest unify-sequence-test
  (testing "Unifying sequences"
    (let [x (u/lvar "x")
          y (u/lvar "y")]
      (is (= {x 1 y 2} (u/unify [x y] [1 2] {})))
      (is (= {} (u/unify [1 2 3] [1 2 3] {})))
      (is (nil? (u/unify [1 2] [1 2 3] {})))
      (is (nil? (u/unify [1 2 3] [1 2] {}))))))

(deftest unify-nested-test
  (testing "Unifying nested structures"
    (let [x (u/lvar "x")
          y (u/lvar "y")]
      (is (= {x 1 y 2} (u/unify [[x] [y]] [[1] [2]] {})))
      (is (= {x [1 2]} (u/unify [x 3] [[1 2] 3] {}))))))

(deftest unify-map-test
  (testing "Unifying maps"
    (let [x (u/lvar "x")]
      (is (= {x 42} (u/unify {:a x} {:a 42} {})))
      (is (nil? (u/unify {:a 1} {:b 1} {})))
      (is (nil? (u/unify {:a 1} {:a 2} {}))))))

;; ============================================================================
;; Occurs Check Tests
;; ============================================================================

(deftest occurs-check-test
  (testing "Occurs check prevents infinite structures"
    (let [x (u/lvar "x")]
      (is (nil? (u/unify x [x] {})))
      (is (nil? (u/unify x [1 [x]] {}))))))

;; ============================================================================
;; Reification Tests
;; ============================================================================

(deftest reify-test
  (testing "Reifying terms"
    (let [x (u/lvar "x")
          y (u/lvar "y")
          subs {x 42}]
      (is (= 42 (u/reify x subs)))
      (is (= '_0 (u/reify y subs)))
      (is (= [42 '_0] (u/reify [x y] subs))))))

;; ============================================================================
;; Symbolize Term Tests
;; ============================================================================

(deftest symbolize-term-test
  (testing "Converting ?-prefixed symbols to logic variables"
    (let [[term _] (u/symbolize-term '(parent ?x ?y))]
      (is (= 'parent (first term)))
      (is (u/lvar? (second term)))
      (is (u/lvar? (nth term 2)))))
  
  (testing "Same symbols get same variables"
    (let [[term _] (u/symbolize-term '[?x ?y ?x])]
      (is (= (first term) (nth term 2))))))

