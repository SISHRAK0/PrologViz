(ns logicflow.front.subs
  "Re-frame subscriptions for LogicFlow UI."
  (:require [re-frame.core :as rf]))

;; ============================================================================
;; Basic Subscriptions
;; ============================================================================

(rf/reg-sub :facts (fn [db] (:facts db)))
(rf/reg-sub :rules (fn [db] (:rules db)))
(rf/reg-sub :history (fn [db] (:history db)))
(rf/reg-sub :stats (fn [db] (:stats db)))
(rf/reg-sub :examples (fn [db] (:examples db)))

;; ============================================================================
;; UI State Subscriptions
;; ============================================================================

(rf/reg-sub :loading (fn [db] (:loading db)))
(rf/reg-sub :error (fn [db] (:error db)))
(rf/reg-sub :view (fn [db] (:view db)))
(rf/reg-sub :selected-predicate (fn [db] (:selected-predicate db)))
(rf/reg-sub :ws-connected (fn [db] (:ws-connected db)))

;; ============================================================================
;; Query Subscriptions
;; ============================================================================

(rf/reg-sub :query-input (fn [db] (:query-input db)))
(rf/reg-sub :query-results (fn [db] (:query-results db)))
(rf/reg-sub :query-trace (fn [db] (:query-trace db)))
(rf/reg-sub :trace-enabled (fn [db] (:trace-enabled db)))
(rf/reg-sub :inference-tree (fn [db] (:inference-tree db)))

;; ============================================================================
;; REPL Subscriptions
;; ============================================================================

(rf/reg-sub :repl-input (fn [db] (:repl-input db)))
(rf/reg-sub :repl-history (fn [db] (:repl-history db)))

;; ============================================================================
;; Spy Points Subscriptions
;; ============================================================================

(rf/reg-sub :spy-points (fn [db] (:spy-points db)))
(rf/reg-sub :spy-log (fn [db] (:spy-log db)))
(rf/reg-sub :spy-stats (fn [db] (:spy-stats db)))
(rf/reg-sub :spy-input (fn [db] (:spy-input db)))

;; ============================================================================
;; Puzzles Subscriptions
;; ============================================================================

(rf/reg-sub :nqueens-n (fn [db] (:nqueens-n db)))
(rf/reg-sub :nqueens-solutions (fn [db] (:nqueens-solutions db)))
(rf/reg-sub :sudoku-puzzle (fn [db] (:sudoku-puzzle db)))
(rf/reg-sub :sudoku-solution (fn [db] (:sudoku-solution db)))
(rf/reg-sub :einstein-solution (fn [db] (:einstein-solution db)))

;; ============================================================================
;; Derived Subscriptions
;; ============================================================================

(rf/reg-sub
 :predicate-list
 (fn [db]
   (let [fact-preds (set (keys (:facts db)))
         rule-preds (set (keys (:rules db)))]
     (sort (into fact-preds rule-preds)))))