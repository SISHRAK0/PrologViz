(ns logicflow.web.ws
  "WebSocket handlers for real-time updates."
  (:require [org.httpkit.server :as http-kit]
            [cheshire.core :as json]
            [logicflow.kb :as kb]))

;; ============================================================================
;; Client Management
;; ============================================================================

(defonce clients (atom #{}))

(defn client-count
  "Get the number of connected clients."
  []
  (count @clients))

(defn add-client!
  "Add a new client connection."
  [channel]
  (swap! clients conj channel)
  (println (str "Client connected. Total: " (client-count))))

(defn remove-client!
  "Remove a client connection."
  [channel]
  (swap! clients disj channel)
  (println (str "Client disconnected. Total: " (client-count))))

;; ============================================================================
;; Message Handling
;; ============================================================================

(defn send-to-client!
  "Send a message to a specific client."
  [channel data]
  (when (http-kit/open? channel)
    (http-kit/send! channel (json/generate-string data))))

(defn broadcast!
  "Broadcast a message to all connected clients."
  [data]
  (let [message (json/generate-string data)]
    (doseq [channel @clients]
      (when (http-kit/open? channel)
        (http-kit/send! channel message)))))

(defn handle-message
  "Handle incoming WebSocket message."
  [channel data]
  (try
    (let [message (json/parse-string data true)]
      (case (:type message)
        ;; Subscribe to updates
        "subscribe"
        (send-to-client! channel {:type "subscribed"
                                   :message "Subscribed to updates"})
        
        ;; Ping/pong for keepalive
        "ping"
        (send-to-client! channel {:type "pong"
                                   :timestamp (System/currentTimeMillis)})
        
        ;; Get current state
        "get-state"
        (send-to-client! channel {:type "state"
                                   :facts (kb/get-all-facts)
                                   :rules (kb/get-all-rules)
                                   :stats (kb/get-stats)})
        
        ;; Unknown message type
        (send-to-client! channel {:type "error"
                                   :message (str "Unknown message type: " (:type message))})))
    (catch Exception e
      (send-to-client! channel {:type "error"
                                 :message (str "Error processing message: " (.getMessage e))}))))

;; ============================================================================
;; WebSocket Handler
;; ============================================================================

(defn websocket-handler
  "WebSocket connection handler."
  [request]
  (http-kit/with-channel request channel
    (add-client! channel)
    
    ;; Send initial state
    (send-to-client! channel {:type "connected"
                               :message "Connected to LogicFlow"
                               :timestamp (System/currentTimeMillis)})
    
    ;; Handle incoming messages
    (http-kit/on-receive channel
                          (fn [data]
                            (handle-message channel data)))
    
    ;; Handle disconnect
    (http-kit/on-close channel
                        (fn [_status]
                          (remove-client! channel)))))

;; ============================================================================
;; KB Watchers
;; ============================================================================

(def watcher-id ::websocket-notifier)

(defn setup-kb-watchers!
  "Setup watchers on KB refs to broadcast changes."
  []
  (kb/add-watcher! watcher-id
                   (fn [event]
                     (broadcast! event))))

(defn cleanup-kb-watchers!
  "Remove KB watchers."
  []
  (kb/remove-watcher! watcher-id))

;; ============================================================================
;; Utilities
;; ============================================================================

(defn close-all-connections!
  "Close all client connections."
  []
  (doseq [channel @clients]
    (http-kit/close channel))
  (reset! clients #{}))

