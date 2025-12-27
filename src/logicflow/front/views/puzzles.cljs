(ns logicflow.front.views.puzzles
  "Puzzles view - classic logic puzzles solved with backtracking."
  (:require [re-frame.core :as rf]
            [logicflow.front.components.common :as c]))

(defn puzzles-view
  "View for solving classic logic puzzles."
  []
  (let [loading @(rf/subscribe [:loading])
        nqueens-n @(rf/subscribe [:nqueens-n])
        nqueens-solutions @(rf/subscribe [:nqueens-solutions])
        einstein-solution @(rf/subscribe [:einstein-solution])
        sudoku-puzzle @(rf/subscribe [:sudoku-puzzle])
        sudoku-solution @(rf/subscribe [:sudoku-solution])]
    [:div.puzzles-view
     [:h2 "🧩 Classic Puzzles"]
     
     ;; N-Queens Section
     [:div.puzzle-section
      [:h3 "♛ N-Queens Problem"]
      [:p "Place N queens on an NxN chessboard so that no two queens attack each other."]
      [:div.puzzle-controls
       [:label "Board size (N): "]
       [:input {:type "number"
                :min 4
                :max 12
                :value nqueens-n
                :on-change #(rf/dispatch [:set-nqueens-n (js/parseInt (-> % .-target .-value))])}]
       [:button.btn.btn-primary
        {:on-click #(rf/dispatch [:solve-nqueens])
         :disabled loading}
        (if loading "Solving..." "Solve N-Queens")]]
      (when (seq nqueens-solutions)
        [:div.nqueens-results
         [:h4 (str "Found " (count nqueens-solutions) " solution(s)")]
         [:div.solutions-grid
          (for [[idx sol] (map-indexed vector (take 4 nqueens-solutions))]
            ^{:key idx}
            [:div.solution
             [:div.solution-title (str "Solution " (inc idx))]
             [c/queens-board sol]
             [:div.solution-notation (pr-str sol)]])]])]
     
     ;; Einstein's Riddle Section
     [:div.puzzle-section
      [:h3 "🏠 Einstein's Riddle"]
      [:p "The famous logic puzzle: 5 houses, 5 nationalities, 5 colors, 5 drinks, 5 pets, 5 cigarettes. Who owns the fish?"]
      [:button.btn.btn-primary
       {:on-click #(rf/dispatch [:solve-einstein])
        :disabled loading}
       (if loading "Solving..." "Solve Einstein's Riddle")]
      (when einstein-solution
        [:div.einstein-results
         [:h4.answer (:answer einstein-solution)]
         [:table.einstein-table
          [:thead
           [:tr [:th "Position"] [:th "Nation"] [:th "Color"] [:th "Drink"] [:th "Pet"] [:th "Cigarette"]]]
          [:tbody
           (for [house (:solution einstein-solution)]
             ^{:key (:position house)}
             [:tr
              [:td (:position house)]
              [:td (name (:nation house))]
              [:td (name (:color house))]
              [:td (name (:drink house))]
              [:td (name (:pet house))]
              [:td (name (:cigarette house))]])]]])]
     
     ;; Sudoku Section
     [:div.puzzle-section
      [:h3 "🔢 Sudoku"]
      [:p "Solve a classic 9x9 Sudoku puzzle using backtracking."]
      (let [default-puzzle [[5 3 0 0 7 0 0 0 0]
                            [6 0 0 1 9 5 0 0 0]
                            [0 9 8 0 0 0 0 6 0]
                            [8 0 0 0 6 0 0 0 3]
                            [4 0 0 8 0 3 0 0 1]
                            [7 0 0 0 2 0 0 0 6]
                            [0 6 0 0 0 0 2 8 0]
                            [0 0 0 4 1 9 0 0 5]
                            [0 0 0 0 8 0 0 7 9]]]
        [:div
         [:div.puzzle-controls
          [:button.btn.btn-primary
           {:on-click #(rf/dispatch [:solve-sudoku])
            :disabled loading}
           (if loading "Solving..." "Solve Sudoku")]]
         (if sudoku-solution
           [:div.sudoku-results
            [:div.sudoku-comparison
             [:div.sudoku-panel
              [:h4 "Puzzle"]
              [c/sudoku-grid (or sudoku-puzzle default-puzzle) false]]
             [:div.sudoku-arrow "→"]
             [:div.sudoku-panel
              [:h4 "Solution"]
              [c/sudoku-grid sudoku-solution true]]]]
           [:div.sudoku-preview
            [:h4 "Preview"]
            [c/sudoku-grid default-puzzle false]])])]]))