(ns logicflow.web.server
  "HTTP server for LogicFlow web interface.
   Uses HTTP-Kit for both HTTP and WebSocket support."
  (:require [compojure.core :refer [defroutes GET POST DELETE routes]]
            [compojure.route :as route]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.util.response :as response]
            [org.httpkit.server :as http-kit]
            [cheshire.core :as json]
            [logicflow.web.api :as api]
            [logicflow.web.ws :as ws]
            [logicflow.kb :as kb]))

;; ============================================================================
;; Server State
;; ============================================================================

(defonce server (atom nil))
(defonce config (atom {:port 3000
                       :host "0.0.0.0"}))

;; ============================================================================
;; Middleware
;; ============================================================================

(defn wrap-cors-headers
  "Add CORS headers to all responses."
  [handler]
  (fn [request]
    (let [response (handler request)]
      (-> response
          (assoc-in [:headers "Access-Control-Allow-Origin"] "*")
          (assoc-in [:headers "Access-Control-Allow-Methods"] "GET, POST, PUT, DELETE, OPTIONS")
          (assoc-in [:headers "Access-Control-Allow-Headers"] "Content-Type, Authorization")))))

(defn wrap-exception
  "Catch exceptions and return proper error responses."
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        {:status 500
         :headers {"Content-Type" "application/json"}
         :body (json/generate-string {:error (.getMessage e)
                                       :type (str (type e))})}))))

(defn wrap-logging
  "Log all requests."
  [handler]
  (fn [request]
    (let [start (System/currentTimeMillis)
          response (handler request)
          duration (- (System/currentTimeMillis) start)]
      (println (format "[%s] %s %s - %dms"
                       (name (:request-method request))
                       (:uri request)
                       (:status response)
                       duration))
      response)))

;; ============================================================================
;; Static Files
;; ============================================================================

(defn serve-index
  "Serve the index.html file."
  [_request]
  (if-let [resource (clojure.java.io/resource "public/index.html")]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (slurp resource)}
    {:status 404
     :body "Not found"}))

;; ============================================================================
;; Routes
;; ============================================================================

(defroutes api-routes
  ;; Knowledge Base API
  (GET "/api/facts" [] api/get-facts)
  (POST "/api/facts" [] api/add-fact)
  (DELETE "/api/facts" [] api/remove-fact)
  
  (GET "/api/rules" [] api/get-rules)
  (POST "/api/rules" [] api/add-rule)
  
  (POST "/api/query" [] api/execute-query)
  
  ;; REPL
  (POST "/api/repl" [] api/eval-repl)
  
  ;; History & Stats
  (GET "/api/history" [] api/get-history)
  (GET "/api/stats" [] api/get-stats)
  
  ;; Trace
  (GET "/api/trace" [] api/get-trace)
  (POST "/api/trace/clear" [] api/clear-trace)
  
  ;; Management
  (POST "/api/clear" [] api/clear-kb)
  (POST "/api/load-example" [] api/load-example)
  (GET "/api/examples" [] api/list-examples)
  
  ;; Persistence
  (GET "/api/export" [] api/export-kb)
  (POST "/api/import" [] api/import-kb)
  (POST "/api/save" [] api/save-kb)
  (POST "/api/load-file" [] api/load-kb-file)
  (GET "/api/export-prolog" [] api/export-prolog)
  (POST "/api/backup" [] api/create-backup)
  (GET "/api/backups" [] api/list-backups)
  
  ;; Tabling
  (GET "/api/tabling" [] api/get-table-info)
  (POST "/api/tabling/clear" [] api/clear-tables)
  
  ;; Spy Points
  (GET "/api/spy" [] api/get-spy-points)
  (POST "/api/spy" [] api/add-spy-point)
  (DELETE "/api/spy" [] api/remove-spy-point)
  (POST "/api/spy/clear" [] api/clear-spy-points)
  (GET "/api/spy/log" [] api/get-spy-log)
  (POST "/api/spy/log/clear" [] api/clear-spy-log)
  
  ;; Puzzle Solvers
  (POST "/api/solve/nqueens" [] api/solve-nqueens-api)
  (POST "/api/solve/sudoku" [] api/solve-sudoku-api)
  (GET "/api/solve/einstein" [] api/solve-einstein-api))

(defroutes static-routes
  (GET "/" [] serve-index)
  (route/resources "/")
  (route/not-found {:status 404 :body "Not found"}))

(def app-routes
  (routes
   ;; WebSocket endpoint
   (GET "/ws" [] ws/websocket-handler)
   
   ;; API routes
   api-routes
   
   ;; Static files
   static-routes))

;; ============================================================================
;; App Handler
;; ============================================================================

(def app
  (-> app-routes
      wrap-keyword-params
      wrap-params
      (wrap-json-body {:keywords? true})
      wrap-json-response
      wrap-cors-headers
      wrap-exception
      wrap-logging))

;; ============================================================================
;; Server Control
;; ============================================================================

(defn start!
  "Start the HTTP server.
   Options:
     :port - Server port (default 3000)
     :host - Server host (default 0.0.0.0)"
  [& {:keys [port host] :or {port 3000 host "0.0.0.0"}}]
  (when @server
    (println "Server already running, stopping first...")
    (@server :timeout 100))
  
  (swap! config assoc :port port :host host)
  
  ;; Setup KB watchers for WebSocket notifications
  (ws/setup-kb-watchers!)
  
  (reset! server
          (http-kit/run-server app
                                {:port port
                                 :host host
                                 :max-body (* 10 1024 1024)}))
  
  (println (str "\n🚀 LogicFlow server started on http://" host ":" port))
  (println "   API available at /api/*")
  (println "   WebSocket at /ws")
  (println "\n   Press Ctrl+C to stop.\n"))

(defn stop!
  "Stop the HTTP server."
  []
  (when @server
    (ws/cleanup-kb-watchers!)
    (@server :timeout 100)
    (reset! server nil)
    (println "Server stopped.")))

(defn restart!
  "Restart the HTTP server."
  []
  (stop!)
  (Thread/sleep 500)
  (start! :port (:port @config) :host (:host @config)))

;; ============================================================================
;; REPL Helpers
;; ============================================================================

(defn server-status
  "Get the current server status."
  []
  (if @server
    {:status :running
     :port (:port @config)
     :host (:host @config)
     :clients (ws/client-count)}
    {:status :stopped}))
