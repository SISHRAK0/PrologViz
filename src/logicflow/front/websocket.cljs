(ns logicflow.front.websocket
  "WebSocket connection management for LogicFlow UI."
  (:require [re-frame.core :as rf]))

(def ws-url (str "ws://" (.-host js/location) "/ws"))

(defonce ws-connection (atom nil))

(defn connect-ws!
  "Establish WebSocket connection to the server."
  []
  (when-let [old-ws @ws-connection]
    (.close old-ws))
  
  (let [ws (js/WebSocket. ws-url)]
    (set! (.-onopen ws)
          (fn [_]
            (println "WebSocket connected")
            (rf/dispatch [:ws-connected])))
    
    (set! (.-onclose ws)
          (fn [_]
            (println "WebSocket disconnected")
            (rf/dispatch [:ws-disconnected])
            (js/setTimeout connect-ws! 3000)))
    
    (set! (.-onmessage ws)
          (fn [event]
            (let [data (js->clj (js/JSON.parse (.-data event)) :keywordize-keys true)]
              (rf/dispatch [:ws-message data]))))
    
    (set! (.-onerror ws)
          (fn [error]
            (println "WebSocket error:" error)))
    
    (reset! ws-connection ws)))

(defn disconnect-ws!
  "Close WebSocket connection."
  []
  (when-let [ws @ws-connection]
    (.close ws)
    (reset! ws-connection nil)))

(defn send-message!
  "Send a message through WebSocket."
  [message]
  (when-let [ws @ws-connection]
    (when (= (.-readyState ws) 1)
      (.send ws (js/JSON.stringify (clj->js message))))))