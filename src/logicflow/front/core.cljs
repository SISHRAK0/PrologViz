(ns logicflow.front.core
  "Main entry point for LogicFlow frontend application."
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [re-frame.core :as rf]
            ;; Load events and subscriptions
            [logicflow.front.events]
            [logicflow.front.subs]
            ;; WebSocket
            [logicflow.front.websocket :as ws]
            ;; Components
            [logicflow.front.components.common :as c]
            ;; Views
            [logicflow.front.views.welcome :refer [welcome-view]]
            [logicflow.front.views.dashboard :refer [dashboard-view]]
            [logicflow.front.views.facts :refer [facts-view]]
            [logicflow.front.views.rules :refer [rules-view]]
            [logicflow.front.views.query :refer [query-view]]
            [logicflow.front.views.repl :refer [repl-view]]
            [logicflow.front.views.trace :refer [trace-view]]
            [logicflow.front.views.spy :refer [spy-view]]
            [logicflow.front.views.puzzles :refer [puzzles-view]]
            [logicflow.front.views.history :refer [history-view]]))

;; ============================================================================
;; Main Content Router
;; ============================================================================

(defn main-content
  "Main content area with view routing."
  []
  (let [view @(rf/subscribe [:view])
        loading @(rf/subscribe [:loading])]
    [:main.main-content
     [c/error-banner]
     (when loading
       [:div.loading-overlay [c/loading-spinner]])
     (case view
       :welcome [welcome-view]
       :dashboard [dashboard-view]
       :facts [facts-view]
       :rules [rules-view]
       :query [query-view]
       :repl [repl-view]
       :trace [trace-view]
       :spy [spy-view]
       :puzzles [puzzles-view]
       :history [history-view]
       [welcome-view])]))

;; ============================================================================
;; Root Application Component
;; ============================================================================

(defn app
  "Root application component."
  []
  [:div.app
   [c/sidebar]
   [main-content]])

;; ============================================================================
;; Initialization
;; ============================================================================

(defn ^:export init!
  "Initialize the LogicFlow UI application."
  []
  (println "Initializing LogicFlow UI...")
  (rf/dispatch-sync [:initialize])
  (ws/connect-ws!)
  (rf/dispatch [:load-data])
  (rdom/render [app] (.getElementById js/document "app")))

(defn ^:dev/after-load reload!
  "Hot reload handler for development."
  []
  (println "Hot reloading...")
  (rdom/render [app] (.getElementById js/document "app")))