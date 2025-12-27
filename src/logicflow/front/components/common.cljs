(ns logicflow.front.components.common
  "Common reusable UI components for LogicFlow."
  (:require [re-frame.core :as rf]))

;; ============================================================================
;; Loading & Status Components
;; ============================================================================

(defn loading-spinner
  "Animated loading spinner."
  []
  [:div.loading-spinner
   [:div.spinner]])

(defn error-banner
  "Dismissable error message banner."
  []
  (let [error @(rf/subscribe [:error])]
    (when error
      [:div.error-banner
       [:span error]
       [:button.dismiss {:on-click #(rf/dispatch [:dismiss-error])} "×"]])))

(defn connection-status
  "WebSocket connection status indicator."
  []
  (let [connected @(rf/subscribe [:ws-connected])]
    [:div.connection-status {:class (if connected "connected" "disconnected")}
     [:span.dot]
     (if connected "Connected" "Disconnected")]))

;; ============================================================================
;; Navigation Components
;; ============================================================================

(defn nav-item
  "Navigation menu item."
  [view label icon]
  (let [current-view @(rf/subscribe [:view])]
    [:button.nav-item 
     {:class (when (= current-view view) "active")
      :on-click #(rf/dispatch [:set-view view])}
     [:span.icon icon]
     [:span.label label]]))

(defn sidebar
  "Main navigation sidebar."
  []
  [:nav.sidebar
   [:div.logo
    [:h1 "⚡ LogicFlow"]]
   [:div.nav-items
    [nav-item :welcome "Welcome" "📖"]
    [nav-item :dashboard "Dashboard" "📊"]
    [nav-item :facts "Facts" "📝"]
    [nav-item :rules "Rules" "⚙️"]
    [nav-item :query "Query" "🔍"]
    [nav-item :repl "REPL" "💻"]
    [nav-item :trace "Trace" "🌳"]
    [nav-item :spy "Spy" "🔎"]
    [nav-item :puzzles "Puzzles" "🧩"]
    [nav-item :history "History" "📜"]]
   [:div.sidebar-footer
    [connection-status]]])

;; ============================================================================
;; Card Components
;; ============================================================================

(defn stats-card
  "Statistics display card."
  [label value icon]
  [:div.stats-card
   [:div.stats-icon icon]
   [:div.stats-content
    [:div.stats-value value]
    [:div.stats-label label]]])

;; ============================================================================
;; Puzzle Components
;; ============================================================================

(defn queens-board
  "N-Queens solution board visualization."
  [solution]
  (let [n (count solution)]
    [:div.queens-board
     (for [row (range n)]
       ^{:key row}
       [:div.board-row
        (for [col (range 1 (inc n))]
          ^{:key col}
          [:div.board-cell {:class (if (= col (nth solution row)) "queen" "")}
           (when (= col (nth solution row)) "♛")])])]))

(defn sudoku-grid
  "Sudoku puzzle grid visualization."
  [grid solved?]
  (when (and grid (seq grid))
    [:div.sudoku-grid {:class (when solved? "solved")}
     (doall
      (for [row (range 9)]
        ^{:key row}
        [:div.sudoku-row {:class (when (#{2 5} row) "border-bottom")}
         (doall
          (for [col (range 9)]
            ^{:key col}
            [:div.sudoku-cell {:class (str (when (#{2 5} col) "border-right ")
                                           (when (zero? (get-in grid [row col] 0)) "empty"))}
             (let [v (get-in grid [row col] 0)]
               (when (pos? v) v))]))]))]))