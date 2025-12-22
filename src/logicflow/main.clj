(ns logicflow.main
  "Main entry point for LogicFlow application."
  (:require [logicflow.core :as core]
            [logicflow.kb :as kb]
            [logicflow.web.server :as server])
  (:gen-class))

(defn -main
  "Start the LogicFlow application."
  [& args]
  (let [port (if (seq args)
               (parse-long (first args))
               3000)]
    (println "\n╔════════════════════════════════════════╗")
    (println "║       LogicFlow - Logic Programming     ║")
    (println "║        DSL with Web Visualizer          ║")
    (println "╚════════════════════════════════════════╝\n")
    
    ;; Load default example
    (core/load-family-example!)
    
    ;; Start server
    (server/start! :port port)
    
    ;; Keep the main thread alive
    @(promise)))

