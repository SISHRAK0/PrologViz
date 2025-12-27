(ns logicflow.front.db
  "Application state and default database for LogicFlow UI.")

(def api-base "")

(def default-db
  {:facts {}
   :rules {}
   :history []
   :stats {}
   :query-input "(grandparent ?x :ann)"
   :query-results []
   :query-trace nil
   :repl-input ""
   :repl-history []
   :selected-predicate nil
   :ws-connected false
   :loading false
   :error nil
   :view :welcome
   :trace-enabled false
   :examples []
   :inference-tree nil
   ;; Spy points
   :spy-points []
   :spy-log []
   :spy-stats {}
   :spy-input ""
   ;; Puzzles
   :nqueens-n 8
   :nqueens-solutions []
   :sudoku-puzzle nil
   :sudoku-solution nil
   :einstein-solution nil})