(ns user
  "Development namespace for REPL-driven development.
   Loaded automatically when starting a REPL with the :dev alias."
  (:require [logicflow.core :refer :all]
            [logicflow.kb :as kb]
            [logicflow.unify :as u]
            [logicflow.search :as s]
            [logicflow.web.server :as server]))

(println "\n╔════════════════════════════════════════╗")
(println "║     LogicFlow Development REPL         ║")
(println "╚════════════════════════════════════════╝\n")

(println "Available namespaces:")
(println "  logicflow.core   - DSL macros (deffact, defrule, query, etc.)")
(println "  logicflow.kb     - Knowledge base operations")
(println "  logicflow.unify  - Unification algorithm")
(println "  logicflow.search - Backtracking search")
(println "  logicflow.web.server - Web server control")
(println "")
(println "Quick start:")
(println "  (load-family-example!)   ; Load example KB")
(println "  (show-kb)                ; Show facts & rules")
(println "  (query (parent ?x ?y))   ; Run a query")
(println "  (server/start!)          ; Start web server")
(println "")

(defn go
  "Start everything for development."
  []
  (load-family-example!)
  (server/start! :port 3000)
  (println "\n✓ Ready! Open http://localhost:3000"))

(defn reset
  "Reset the knowledge base and restart the server."
  []
  (server/stop!)
  (kb/clear-kb!)
  (load-family-example!)
  (server/start! :port 3000)
  (println "\n✓ Reset complete!"))

(defn halt
  "Stop the server."
  []
  (server/stop!))

