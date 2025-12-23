(ns logicflow.web.ui
  "ClojureScript frontend for LogicFlow visualizer."
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [re-frame.core :as rf]
            [ajax.core :as ajax]
            [day8.re-frame.http-fx]
            [clojure.string :as str]
            [cljs.reader :as reader]))

;; ============================================================================
;; Configuration
;; ============================================================================

(def api-base "")
(def ws-url (str "ws://" (.-host js/location) "/ws"))

;; ============================================================================
;; WebSocket
;; ============================================================================

(defonce ws-connection (atom nil))

(defn connect-ws! []
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

;; ============================================================================
;; Re-frame Database
;; ============================================================================

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
   :view :dashboard
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

;; ============================================================================
;; Events
;; ============================================================================

(rf/reg-event-db
 :initialize
 (fn [_ _]
   default-db))

(rf/reg-event-fx
 :load-data
 (fn [{:keys [db]} _]
   {:db (assoc db :loading true)
    :fx [[:dispatch [:fetch-facts]]
         [:dispatch [:fetch-rules]]
         [:dispatch [:fetch-stats]]
         [:dispatch [:fetch-history]]
         [:dispatch [:fetch-examples]]]}))

(rf/reg-event-fx
 :fetch-facts
 (fn [{:keys [db]} _]
   {:http-xhrio {:method :get
                 :uri (str api-base "/api/facts")
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:facts-loaded]
                 :on-failure [:api-error]}}))

(rf/reg-event-db
 :facts-loaded
 (fn [db [_ response]]
   (assoc db :facts (get-in response [:data :facts]) :loading false)))

(rf/reg-event-fx
 :fetch-rules
 (fn [{:keys [db]} _]
   {:http-xhrio {:method :get
                 :uri (str api-base "/api/rules")
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:rules-loaded]
                 :on-failure [:api-error]}}))

(rf/reg-event-db
 :rules-loaded
 (fn [db [_ response]]
   (assoc db :rules (get-in response [:data :rules]))))

(rf/reg-event-fx
 :fetch-stats
 (fn [{:keys [db]} _]
   {:http-xhrio {:method :get
                 :uri (str api-base "/api/stats")
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:stats-loaded]
                 :on-failure [:api-error]}}))

(rf/reg-event-db
 :stats-loaded
 (fn [db [_ response]]
   (assoc db :stats (:data response))))

(rf/reg-event-fx
 :fetch-history
 (fn [{:keys [db]} _]
   {:http-xhrio {:method :get
                 :uri (str api-base "/api/history?limit=50")
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:history-loaded]
                 :on-failure [:api-error]}}))

(rf/reg-event-db
 :history-loaded
 (fn [db [_ response]]
   (assoc db :history (get-in response [:data :history]))))

(rf/reg-event-fx
 :fetch-examples
 (fn [{:keys [db]} _]
   {:http-xhrio {:method :get
                 :uri (str api-base "/api/examples")
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:examples-loaded]
                 :on-failure [:api-error]}}))

(rf/reg-event-db
 :examples-loaded
 (fn [db [_ response]]
   (assoc db :examples (get-in response [:data :examples]))))

(rf/reg-event-db
 :api-error
 (fn [db [_ error]]
   (assoc db :error (str "API Error: " (:status-text error)) :loading false)))

(rf/reg-event-db
 :set-query-input
 (fn [db [_ value]]
   (assoc db :query-input value)))

(rf/reg-event-db
 :toggle-trace
 (fn [db _]
   (update db :trace-enabled not)))

(rf/reg-event-fx
 :execute-query
 (fn [{:keys [db]} _]
   (let [input (:query-input db)
         trace? (:trace-enabled db)
         goals (try
                 (let [parsed (reader/read-string input)]
                   (if (and (list? parsed) (seq parsed))
                     [(vec parsed)]
                     []))
                 (catch :default _
                   []))]
     (if (seq goals)
       {:db (assoc db :loading true :query-results [] :error nil :query-trace nil)
        :http-xhrio {:method :post
                     :uri (str api-base "/api/query")
                     :params {:goals goals :trace trace? :limit 100}
                     :format (ajax/json-request-format)
                     :response-format (ajax/json-response-format {:keywords? true})
                     :on-success [:query-success]
                     :on-failure [:query-error]}}
       {:db (assoc db :error "Invalid query format. Use: (predicate ?var value)")}))))

(rf/reg-event-db
 :query-success
 (fn [db [_ response]]
   (assoc db 
          :query-results (get-in response [:data :results])
          :query-trace (get-in response [:data :trace])
          :inference-tree (get-in response [:data :trace :tree])
          :loading false
          :error nil)))

(rf/reg-event-db
 :query-error
 (fn [db [_ error]]
   (assoc db :error (str "Query failed: " (get-in error [:response :message])) :loading false)))

;; REPL Events
(rf/reg-event-db
 :set-repl-input
 (fn [db [_ value]]
   (assoc db :repl-input value)))

(rf/reg-event-fx
 :eval-repl
 (fn [{:keys [db]} _]
   (let [code (:repl-input db)]
     (when (not (str/blank? code))
       {:db (assoc db :loading true)
        :http-xhrio {:method :post
                     :uri (str api-base "/api/repl")
                     :params {:code code}
                     :format (ajax/json-request-format)
                     :response-format (ajax/json-response-format {:keywords? true})
                     :on-success [:repl-success code]
                     :on-failure [:repl-error code]}}))))

(rf/reg-event-db
 :repl-success
 (fn [db [_ code response]]
   (-> db
       (assoc :loading false :repl-input "")
       (update :repl-history conj {:type :input :text code})
       (update :repl-history conj {:type :output :text (get-in response [:data :result])}))))

(rf/reg-event-db
 :repl-error
 (fn [db [_ code error]]
   (-> db
       (assoc :loading false)
       (update :repl-history conj {:type :input :text code})
       (update :repl-history conj {:type :error :text (str "Error: " (get-in error [:response :message]))}))))

(rf/reg-event-fx
 :add-fact
 (fn [{:keys [db]} [_ predicate args]]
   {:http-xhrio {:method :post
                 :uri (str api-base "/api/facts")
                 :params {:predicate predicate :args args}
                 :format (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:fact-added]
                 :on-failure [:api-error]}}))

(rf/reg-event-fx
 :fact-added
 (fn [{:keys [db]} _]
   {:dispatch [:load-data]}))

(rf/reg-event-fx
 :load-example
 (fn [{:keys [db]} [_ example]]
   {:db (assoc db :loading true)
    :http-xhrio {:method :post
                 :uri (str api-base "/api/load-example")
                 :params {:example example}
                 :format (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:example-loaded]
                 :on-failure [:api-error]}}))

(rf/reg-event-fx
 :example-loaded
 (fn [{:keys [db]} _]
   {:dispatch [:load-data]}))

(rf/reg-event-fx
 :clear-kb
 (fn [{:keys [db]} _]
   {:http-xhrio {:method :post
                 :uri (str api-base "/api/clear")
                 :format (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:kb-cleared]
                 :on-failure [:api-error]}}))

(rf/reg-event-fx
 :kb-cleared
 (fn [{:keys [db]} _]
   {:dispatch [:load-data]}))

(rf/reg-event-db
 :select-predicate
 (fn [db [_ pred]]
   (assoc db :selected-predicate pred)))

(rf/reg-event-db
 :set-view
 (fn [db [_ view]]
   (assoc db :view view)))

(rf/reg-event-db
 :ws-connected
 (fn [db _]
   (assoc db :ws-connected true)))

(rf/reg-event-db
 :ws-disconnected
 (fn [db _]
   (assoc db :ws-connected false)))

(rf/reg-event-fx
 :ws-message
 (fn [{:keys [db]} [_ message]]
   (case (:type message)
     "facts-changed" {:dispatch [:fetch-facts]}
     "rules-changed" {:dispatch [:fetch-rules]}
     :fact-added {:dispatch [:load-data]}
     :rule-added {:dispatch [:load-data]}
     :kb-cleared {:dispatch [:load-data]}
     :example-loaded {:dispatch [:load-data]}
     {})))

(rf/reg-event-db
 :dismiss-error
 (fn [db _]
   (assoc db :error nil)))

;; Spy Points Events
(rf/reg-event-db
 :set-spy-input
 (fn [db [_ value]]
   (assoc db :spy-input value)))

(rf/reg-event-fx
 :fetch-spy-points
 (fn [{:keys [db]} _]
   {:http-xhrio {:method :get
                 :uri (str api-base "/api/spy")
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:spy-points-loaded]
                 :on-failure [:api-error]}}))

(rf/reg-event-db
 :spy-points-loaded
 (fn [db [_ response]]
   (assoc db 
          :spy-points (get-in response [:data :spy-points])
          :spy-log (get-in response [:data :spy-log])
          :spy-stats (get-in response [:data :stats]))))

(rf/reg-event-fx
 :add-spy-point
 (fn [{:keys [db]} _]
   (let [pred (:spy-input db)]
     (when (not (str/blank? pred))
       {:db (assoc db :spy-input "")
        :http-xhrio {:method :post
                     :uri (str api-base "/api/spy")
                     :params {:predicate pred}
                     :format (ajax/json-request-format)
                     :response-format (ajax/json-response-format {:keywords? true})
                     :on-success [:spy-point-added]
                     :on-failure [:api-error]}}))))

(rf/reg-event-fx
 :spy-point-added
 (fn [{:keys [db]} _]
   {:dispatch [:fetch-spy-points]}))

(rf/reg-event-fx
 :remove-spy-point
 (fn [{:keys [db]} [_ pred]]
   {:http-xhrio {:method :delete
                 :uri (str api-base "/api/spy")
                 :params {:predicate pred}
                 :format (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:spy-point-removed]
                 :on-failure [:api-error]}}))

(rf/reg-event-fx
 :spy-point-removed
 (fn [{:keys [db]} _]
   {:dispatch [:fetch-spy-points]}))

(rf/reg-event-fx
 :clear-spy-points
 (fn [{:keys [db]} _]
   {:http-xhrio {:method :post
                 :uri (str api-base "/api/spy/clear")
                 :format (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:spy-points-cleared]
                 :on-failure [:api-error]}}))

(rf/reg-event-fx
 :spy-points-cleared
 (fn [{:keys [db]} _]
   {:dispatch [:fetch-spy-points]}))

;; Puzzle Events
(rf/reg-event-db
 :set-nqueens-n
 (fn [db [_ n]]
   (assoc db :nqueens-n n)))

(rf/reg-event-fx
 :solve-nqueens
 (fn [{:keys [db]} _]
   {:db (assoc db :loading true :nqueens-solutions [])
    :http-xhrio {:method :post
                 :uri (str api-base "/api/solve/nqueens")
                 :params {:n (:nqueens-n db)}
                 :format (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:nqueens-solved]
                 :on-failure [:api-error]}}))

(rf/reg-event-db
 :nqueens-solved
 (fn [db [_ response]]
   (assoc db 
          :nqueens-solutions (get-in response [:data :solutions])
          :loading false)))

(rf/reg-event-fx
 :solve-einstein
 (fn [{:keys [db]} _]
   {:db (assoc db :loading true :einstein-solution nil)
    :http-xhrio {:method :get
                 :uri (str api-base "/api/solve/einstein")
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:einstein-solved]
                 :on-failure [:api-error]}}))

(rf/reg-event-db
 :einstein-solved
 (fn [db [_ response]]
   (assoc db 
          :einstein-solution (:data response)
          :loading false)))

(rf/reg-event-fx
 :solve-sudoku
 (fn [{:keys [db]} _]
   {:db (assoc db :loading true :sudoku-solution nil)
    :http-xhrio {:method :post
                 :uri (str api-base "/api/solve/sudoku")
                 :params {:puzzle [[5 3 0 0 7 0 0 0 0]
                                   [6 0 0 1 9 5 0 0 0]
                                   [0 9 8 0 0 0 0 6 0]
                                   [8 0 0 0 6 0 0 0 3]
                                   [4 0 0 8 0 3 0 0 1]
                                   [7 0 0 0 2 0 0 0 6]
                                   [0 6 0 0 0 0 2 8 0]
                                   [0 0 0 4 1 9 0 0 5]
                                   [0 0 0 0 8 0 0 7 9]]}
                 :format (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:sudoku-solved]
                 :on-failure [:api-error]}}))

(rf/reg-event-db
 :sudoku-solved
 (fn [db [_ response]]
   (assoc db 
          :sudoku-puzzle (get-in response [:data :puzzle])
          :sudoku-solution (get-in response [:data :solution])
          :loading false)))

;; ============================================================================
;; Subscriptions
;; ============================================================================

(rf/reg-sub :facts (fn [db] (:facts db)))
(rf/reg-sub :rules (fn [db] (:rules db)))
(rf/reg-sub :history (fn [db] (:history db)))
(rf/reg-sub :stats (fn [db] (:stats db)))
(rf/reg-sub :query-input (fn [db] (:query-input db)))
(rf/reg-sub :query-results (fn [db] (:query-results db)))
(rf/reg-sub :query-trace (fn [db] (:query-trace db)))
(rf/reg-sub :repl-input (fn [db] (:repl-input db)))
(rf/reg-sub :repl-history (fn [db] (:repl-history db)))
(rf/reg-sub :selected-predicate (fn [db] (:selected-predicate db)))
(rf/reg-sub :ws-connected (fn [db] (:ws-connected db)))
(rf/reg-sub :loading (fn [db] (:loading db)))
(rf/reg-sub :error (fn [db] (:error db)))
(rf/reg-sub :view (fn [db] (:view db)))
(rf/reg-sub :trace-enabled (fn [db] (:trace-enabled db)))
(rf/reg-sub :examples (fn [db] (:examples db)))
(rf/reg-sub :inference-tree (fn [db] (:inference-tree db)))
;; Spy points
(rf/reg-sub :spy-points (fn [db] (:spy-points db)))
(rf/reg-sub :spy-log (fn [db] (:spy-log db)))
(rf/reg-sub :spy-stats (fn [db] (:spy-stats db)))
(rf/reg-sub :spy-input (fn [db] (:spy-input db)))
;; Puzzles
(rf/reg-sub :nqueens-n (fn [db] (:nqueens-n db)))
(rf/reg-sub :nqueens-solutions (fn [db] (:nqueens-solutions db)))
(rf/reg-sub :sudoku-puzzle (fn [db] (:sudoku-puzzle db)))
(rf/reg-sub :sudoku-solution (fn [db] (:sudoku-solution db)))
(rf/reg-sub :einstein-solution (fn [db] (:einstein-solution db)))

(rf/reg-sub
 :predicate-list
 (fn [db]
   (let [fact-preds (set (keys (:facts db)))
         rule-preds (set (keys (:rules db)))]
     (sort (into fact-preds rule-preds)))))

;; ============================================================================
;; Components
;; ============================================================================

(defn loading-spinner []
  [:div.loading-spinner
   [:div.spinner]])

(defn error-banner []
  (let [error @(rf/subscribe [:error])]
    (when error
      [:div.error-banner
       [:span error]
       [:button.dismiss {:on-click #(rf/dispatch [:dismiss-error])} "×"]])))

(defn connection-status []
  (let [connected @(rf/subscribe [:ws-connected])]
    [:div.connection-status {:class (if connected "connected" "disconnected")}
     [:span.dot]
     (if connected "Connected" "Disconnected")]))

(defn nav-item [view label icon]
  (let [current-view @(rf/subscribe [:view])]
    [:button.nav-item 
     {:class (when (= current-view view) "active")
      :on-click #(rf/dispatch [:set-view view])}
     [:span.icon icon]
     [:span.label label]]))

(defn sidebar []
  [:nav.sidebar
   [:div.logo
    [:h1 "⚡ LogicFlow"]]
   [:div.nav-items
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

(defn stats-card [label value icon]
  [:div.stats-card
   [:div.stats-icon icon]
   [:div.stats-content
    [:div.stats-value value]
    [:div.stats-label label]]])

(defn dashboard-view []
  (let [stats @(rf/subscribe [:stats])
        facts @(rf/subscribe [:facts])
        rules @(rf/subscribe [:rules])
        examples @(rf/subscribe [:examples])]
    [:div.dashboard
     [:h2 "Dashboard"]
     
     [:div.stats-grid
      [stats-card "Total Facts" (or (:total-facts stats) 0) "📝"]
      [stats-card "Total Rules" (or (:total-rules stats) 0) "⚙️"]
      [stats-card "Predicates" (count (or (:predicates stats) [])) "🏷️"]
      [stats-card "Queries" (or (:queries stats) 0) "🔍"]]
     
     [:div.quick-actions
      [:h3 "Load Examples"]
      [:div.action-buttons
       (for [{:keys [id name]} examples]
         ^{:key id}
         [:button.btn.btn-primary 
          {:on-click #(rf/dispatch [:load-example id])}
          name])
       [:button.btn.btn-danger 
        {:on-click #(rf/dispatch [:clear-kb])}
        "Clear All"]]]
     
     [:div.predicate-overview
      [:h3 "Predicates Overview"]
      [:div.predicate-grid
       (for [[pred fact-set] (sort-by first facts)]
         ^{:key (str "fact-" pred)}
         [:div.predicate-card.fact-card
          {:on-click #(do (rf/dispatch [:select-predicate pred])
                          (rf/dispatch [:set-view :facts]))}
          [:div.pred-name (name pred)]
          [:div.pred-count (str (count fact-set) " facts")]])
       (for [[pred rule-list] (sort-by first rules)]
         ^{:key (str "rule-" pred)}
         [:div.predicate-card.rule-card
          {:on-click #(do (rf/dispatch [:select-predicate pred])
                          (rf/dispatch [:set-view :rules]))}
          [:div.pred-name (name pred)]
          [:div.pred-count (str (count rule-list) " rules")]])]]]))

(defn facts-view []
  (let [facts @(rf/subscribe [:facts])
        selected @(rf/subscribe [:selected-predicate])]
    [:div.facts-view
     [:h2 "Facts"]
     
     [:div.content-split
      [:div.predicate-list
       [:h3 "Predicates"]
       [:ul
        (for [[pred _] (sort-by first facts)]
          ^{:key pred}
          [:li {:class (when (= pred selected) "selected")
                :on-click #(rf/dispatch [:select-predicate pred])}
           (name pred)])]]
      
      [:div.fact-details
       (if selected
         [:div
          [:h3 (str "Facts for " (name selected))]
          [:table.facts-table
           [:thead
            [:tr [:th "Arguments"]]]
           [:tbody
            (for [args (get facts selected)]
              ^{:key (pr-str args)}
              [:tr [:td (pr-str args)]])]]]
         [:div.placeholder "Select a predicate to view facts"])]]]))

(defn rules-view []
  (let [rules @(rf/subscribe [:rules])
        selected @(rf/subscribe [:selected-predicate])]
    [:div.rules-view
     [:h2 "Rules"]
     
     [:div.content-split
      [:div.predicate-list
       [:h3 "Predicates"]
       [:ul
        (for [[pred _] (sort-by first rules)]
          ^{:key pred}
          [:li {:class (when (= pred selected) "selected")
                :on-click #(rf/dispatch [:select-predicate pred])}
           (name pred)])]]
      
      [:div.rule-details
       (if selected
         [:div
          [:h3 (str "Rules for " (name selected))]
          [:div.rules-list
           (for [{:keys [head body id]} (get rules selected)]
             ^{:key id}
             [:div.rule-item
              [:div.rule-head (pr-str head)]
              [:div.rule-arrow ":-"]
              [:div.rule-body
               (for [[idx goal] (map-indexed vector body)]
                 ^{:key idx}
                 [:span.goal (pr-str goal)])]])]]
         [:div.placeholder "Select a predicate to view rules"])]]]))

(defn query-view []
  (let [query-input @(rf/subscribe [:query-input])
        results @(rf/subscribe [:query-results])
        trace @(rf/subscribe [:query-trace])
        trace-enabled @(rf/subscribe [:trace-enabled])
        loading @(rf/subscribe [:loading])]
    [:div.query-view
     [:h2 "Query"]
     
     [:div.query-panel
      [:div.query-input-group
       [:label "Enter your query (Prolog-like syntax):"]
       [:input.query-input
        {:type "text"
         :placeholder "(grandparent ?x :ann)"
         :value query-input
         :on-change #(rf/dispatch [:set-query-input (-> % .-target .-value)])
         :on-key-down #(when (= (.-key %) "Enter")
                         (rf/dispatch [:execute-query]))}]
       [:div.query-controls
        [:label.trace-toggle
         [:input {:type "checkbox"
                  :checked trace-enabled
                  :on-change #(rf/dispatch [:toggle-trace])}]
         " Enable Tracing"]
        [:button.btn.btn-primary
         {:on-click #(rf/dispatch [:execute-query])
          :disabled loading}
         (if loading "Running..." "Execute")]]]
      
      [:div.query-examples
       [:h4 "Example queries:"]
       [:ul
        [:li {:on-click #(rf/dispatch [:set-query-input "(parent ?x ?y)"])}
         [:code "(parent ?x ?y)"] " - Find all parent relationships"]
        [:li {:on-click #(rf/dispatch [:set-query-input "(grandparent ?who :ann)"])}
         [:code "(grandparent ?who :ann)"] " - Who is Ann's grandparent?"]
        [:li {:on-click #(rf/dispatch [:set-query-input "(ancestor :tom ?descendant)"])}
         [:code "(ancestor :tom ?descendant)"] " - Find Tom's descendants"]]]]
     
     [:div.results-panel
      [:h3 "Results"]
      (cond
        loading [loading-spinner]
        (empty? results) [:div.no-results "No results. Run a query to see results."]
        :else
        [:div.results-list
         [:div.result-count (str (count results) " result(s) found")]
         [:table.results-table
          [:thead
           [:tr
            (for [k (keys (first results))]
              ^{:key k} [:th (name k)])]]
          [:tbody
           (for [[idx result] (map-indexed vector results)]
             ^{:key idx}
             [:tr
              (for [[k v] result]
                ^{:key k} [:td (pr-str v)])])]]])]
     
     (when trace
       [:div.trace-panel
        [:h3 "Trace Statistics"]
        [:div.trace-stats
         [:span "Calls: " (get-in trace [:stats :total-calls])]
         [:span "Successes: " (get-in trace [:stats :successes])]
         [:span "Failures: " (get-in trace [:stats :failures])]
         [:span "Max depth: " (get-in trace [:stats :max-depth])]]])]))

(defn repl-view []
  (let [repl-input @(rf/subscribe [:repl-input])
        history @(rf/subscribe [:repl-history])
        loading @(rf/subscribe [:loading])]
    [:div.repl-view
     [:h2 "REPL"]
     
     [:div.repl-container
      [:div.repl-output
       (for [[idx {:keys [type text]}] (map-indexed vector history)]
         ^{:key idx}
         [:div {:class (str "repl-line " (name type))}
          [:span.prompt (if (= type :input) "λ> " "=> ")]
          [:span.text text]])]
      
      [:div.repl-input-area
       [:span.prompt "λ> "]
       [:input.repl-input
        {:type "text"
         :value repl-input
         :placeholder "(query (parent ?x ?y))"
         :on-change #(rf/dispatch [:set-repl-input (-> % .-target .-value)])
         :on-key-down #(when (= (.-key %) "Enter")
                         (rf/dispatch [:eval-repl]))}]
       [:button.btn.btn-primary
        {:on-click #(rf/dispatch [:eval-repl])
         :disabled loading}
        "Run"]]]
     
     [:div.repl-help
      [:h4 "Quick Reference:"]
      [:pre
       "(deffact parent :tom :mary)       ; Add a fact\n"
       "(<- (grandparent ?x ?z)           ; Add a rule\n"
       "    (parent ?x ?y)\n"
       "    (parent ?y ?z))\n"
       "(query (grandparent ?who :ann))   ; Run a query\n"
       "(show-kb)                         ; Show all facts & rules\n"
       "(clear!)                          ; Clear knowledge base"]]]))

(defn build-tree-hierarchy [nodes]
  "Build a hierarchical tree structure from flat nodes with parent references."
  (let [nodes-by-id (into {} (map (juxt :id identity) nodes))
        root-nodes (filter #(nil? (:parent %)) nodes)
        children-map (group-by :parent nodes)]
    (letfn [(build-node [node]
              (let [children (get children-map (:id node) [])]
                (assoc node :children (mapv build-node children))))]
      (mapv build-node root-nodes))))

(defn search-tree-node [node level]
  (let [{:keys [id label args status results children]} node
        has-children (seq children)
        status-class (case status "success" "success" "fail" "fail" "pending")]
    [:div.search-tree-node
     [:div.node-content {:class status-class}
      [:div.node-icon
       (case status
         "success" "✓"
         "fail" "✗"
         "⋯")]
      [:div.node-info
       [:span.node-label label]
       [:span.node-args args]
       (when (and (= status "success") (pos? results))
         [:span.node-results (str results " solution" (when (> results 1) "s"))])]]
     (when has-children
       [:div.node-children
        [:div.connector-line]
        (for [child children]
          ^{:key (:id child)}
          [search-tree-node child (inc level)])])]))

(defn search-tree-visualization [tree]
  (let [nodes (:nodes tree)
        hierarchy (build-tree-hierarchy nodes)]
    [:div.search-tree
     (if (empty? hierarchy)
       [:div.empty-tree "Run a query with tracing enabled to see the search tree."]
       [:div.tree-root
        (for [root-node hierarchy]
          ^{:key (:id root-node)}
          [search-tree-node root-node 0])])]))

(defn trace-view []
  (let [tree @(rf/subscribe [:inference-tree])
        trace @(rf/subscribe [:query-trace])]
    [:div.trace-view
     [:h2 "🌳 Search Tree"]
     
     [:div.trace-instructions
      [:p "Enable " [:strong "tracing"] " in Query view, then execute a query to visualize the backtracking search tree."]]
     
     (if (and tree (seq (:nodes tree)))
       [:div.trace-content
        [:div.search-tree-panel
         [:h3 "Backtracking Search Tree"]
         [:div.tree-legend
          [:span.legend-item.success "✓ Success"]
          [:span.legend-item.fail "✗ Failure"]
          [:span.legend-item.pending "⋯ Pending"]]
         [search-tree-visualization tree]]
        
        [:div.trace-stats-panel
         [:h3 "Statistics"]
         (when-let [stats (get-in trace [:stats])]
           [:div.stats-grid
            [:div.stat-card
             [:div.stat-number (:total-calls stats)]
             [:div.stat-label "Total Calls"]]
            [:div.stat-card
             [:div.stat-number (:successes stats)]
             [:div.stat-label "Successes"]]
            [:div.stat-card
             [:div.stat-number (:failures stats)]
             [:div.stat-label "Failures"]]
            [:div.stat-card
             [:div.stat-number (:max-depth stats)]
             [:div.stat-label "Max Depth"]]])]
        
        (when-let [log (:log trace)]
          [:div.trace-log-panel
           [:h3 "Execution Log"]
           [:div.log-container
            (for [[idx entry] (map-indexed vector (take 50 log))]
              (let [event-type (keyword (:event entry))
                    event-name (if (keyword? (:event entry)) 
                                 (name (:event entry)) 
                                 (str (:event entry)))]
                ^{:key idx}
                [:div.log-entry {:class event-name}
                 [:span.log-indent (apply str (repeat (or (:depth entry) 0) "  "))]
                 [:span.log-event {:class event-name}
                  (case event-type
                    :call "CALL"
                    :exit "EXIT"
                    :fail "FAIL"
                    :redo "REDO"
                    (str/upper-case event-name))]
                 [:span.log-goal (if (keyword? (:goal entry)) 
                                   (name (:goal entry)) 
                                   (str (:goal entry)))]
                 [:span.log-args (:args entry)]]))]])]
       [:div.no-trace
        [:div.empty-state
         [:div.empty-icon "🔍"]
         [:h3 "No trace data"]
         [:p "1. Go to the " [:strong "Query"] " view"]
         [:p "2. Enable " [:strong "Tracing"] " checkbox"]
         [:p "3. Execute a query like " [:code "(grandparent ?x :ann)"]]
         [:p "4. Come back here to see the search tree"]]])]))

(defn history-view []
  (let [history @(rf/subscribe [:history])]
    [:div.history-view
     [:h2 "Transaction History"]
     [:div.timeline
      (if (empty? history)
        [:div.no-history "No transactions yet."]
        (doall
         (for [[idx item] (map-indexed vector (reverse history))]
           ^{:key idx}
           [:div.timeline-item {:class (name (:type item))}
            [:div.timeline-dot]
            [:div.timeline-content
             [:div.timeline-type (name (:type item))]
             [:div.timeline-details
              (case (:type item)
                :assert [:span "Asserted " [:code (str (:predicate item) " " (:args item))]]
                :retract [:span "Retracted " [:code (str (:predicate item) " " (:args item))]]
                :add-rule [:span "Added rule for " [:code (str (:predicate item))]]
                :clear [:span "Knowledge base cleared"]
                :import [:span "Knowledge base imported"]
                [:span (pr-str item)])]
             [:div.timeline-time
              (when (:timestamp item)
                (.toLocaleTimeString (js/Date. (:timestamp item))))]]])))]]))

(defn spy-view []
  (let [spy-points @(rf/subscribe [:spy-points])
        spy-log @(rf/subscribe [:spy-log])
        spy-stats @(rf/subscribe [:spy-stats])
        spy-input @(rf/subscribe [:spy-input])
        predicates @(rf/subscribe [:predicate-list])
        loading @(rf/subscribe [:loading])]
    [:div.spy-view
     [:h2 "🔎 Spy Points"]
     
     [:div.spy-controls
      [:div.spy-add
       [:h3 "Add Spy Point"]
       [:div.spy-input-group
        [:input {:type "text"
                 :placeholder "Enter predicate name (e.g., parent)"
                 :value spy-input
                 :on-change #(rf/dispatch [:set-spy-input (-> % .-target .-value)])
                 :on-key-down #(when (= (.-key %) "Enter")
                                 (rf/dispatch [:add-spy-point]))}]
        [:button.btn.btn-primary
         {:on-click #(rf/dispatch [:add-spy-point])
          :disabled loading}
         "Add Spy"]]
       [:div.quick-spy
        [:span "Quick add: "]
        (for [pred (take 10 predicates)]
          ^{:key pred}
          [:button.btn.btn-small
           {:on-click #(do (rf/dispatch [:set-spy-input (name pred)])
                           (rf/dispatch [:add-spy-point]))}
           (name pred)])]]
      
      [:div.spy-active
       [:h3 "Active Spy Points"]
       (if (empty? spy-points)
         [:div.no-spy "No spy points set. Add one to start debugging."]
         [:div.spy-list
          (for [point spy-points]
            ^{:key point}
            [:div.spy-item
             [:span.spy-name (if (keyword? point) (name point) (str point))]
             [:button.btn.btn-small.btn-danger
              {:on-click #(rf/dispatch [:remove-spy-point (if (keyword? point) (name point) (str point))])}
              "×"]])
          [:button.btn.btn-warning
           {:on-click #(rf/dispatch [:clear-spy-points])}
           "Clear All"]])]]
     
     [:div.spy-stats
      [:h3 "Statistics"]
      [:div.stats-grid
       [:div.stat-item
        [:span.stat-label "Total calls:"]
        [:span.stat-value (or (:total-calls spy-stats) 0)]]
       [:div.stat-item
        [:span.stat-label "Successes:"]
        [:span.stat-value.success (or (:successes spy-stats) 0)]]
       [:div.stat-item
        [:span.stat-label "Failures:"]
        [:span.stat-value.failure (or (:failures spy-stats) 0)]]]]
     
     [:div.spy-log-section
      [:h3 "Spy Log"]
      [:button.btn.btn-small
       {:on-click #(rf/dispatch [:fetch-spy-points])}
       "Refresh"]
      (if (empty? spy-log)
        [:div.no-log "No spy events captured yet. Run a query with spy points active."]
        [:div.spy-log
         (for [[idx entry] (map-indexed vector (take 50 spy-log))]
           ^{:key idx}
           [:div.log-entry {:class (name (:event entry))}
            [:span.event-type (name (:event entry))]
            [:span.predicate (if (keyword? (:goal entry)) (name (:goal entry)) (str (:goal entry)))]
            [:span.args (pr-str (:args entry))]])])]
     
     [:div.spy-help
      [:h4 "How to use Spy Points"]
      [:ol
       [:li "Add a spy point for a predicate you want to debug"]
       [:li "Go to the Query view and run a query that uses that predicate"]
       [:li "Come back here to see the CALL/EXIT/FAIL events"]
       [:li "Use this to understand how predicates are being resolved"]]]]))

(defn queens-board [solution]
  (let [n (count solution)]
    [:div.queens-board
     (for [row (range n)]
       ^{:key row}
       [:div.board-row
        (for [col (range 1 (inc n))]
          ^{:key col}
          [:div.board-cell {:class (if (= col (nth solution row)) "queen" "")}
           (when (= col (nth solution row)) "♛")])])]))

(defn sudoku-grid [grid solved?]
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

(defn puzzles-view []
  (let [loading @(rf/subscribe [:loading])
        nqueens-n @(rf/subscribe [:nqueens-n])
        nqueens-solutions @(rf/subscribe [:nqueens-solutions])
        einstein-solution @(rf/subscribe [:einstein-solution])
        sudoku-puzzle @(rf/subscribe [:sudoku-puzzle])
        sudoku-solution @(rf/subscribe [:sudoku-solution])]
    [:div.puzzles-view
     [:h2 "🧩 Classic Puzzles"]
     
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
             [queens-board sol]
             [:div.solution-notation (pr-str sol)]])]])]
     
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
              [sudoku-grid (or sudoku-puzzle default-puzzle) false]]
             [:div.sudoku-arrow "→"]
             [:div.sudoku-panel
              [:h4 "Solution"]
              [sudoku-grid sudoku-solution true]]]]
           [:div.sudoku-preview
            [:h4 "Preview"]
            [sudoku-grid default-puzzle false]])])]]))

(defn main-content []
  (let [view @(rf/subscribe [:view])
        loading @(rf/subscribe [:loading])]
    [:main.main-content
     [error-banner]
     (when loading
       [:div.loading-overlay [loading-spinner]])
     (case view
       :dashboard [dashboard-view]
       :facts [facts-view]
       :rules [rules-view]
       :query [query-view]
       :repl [repl-view]
       :trace [trace-view]
       :spy [spy-view]
       :puzzles [puzzles-view]
       :history [history-view]
       [dashboard-view])]))

(defn app []
  [:div.app
   [sidebar]
   [main-content]])

;; ============================================================================
;; Initialization
;; ============================================================================

(defn ^:export init! []
  (println "Initializing LogicFlow UI...")
  (rf/dispatch-sync [:initialize])
  (connect-ws!)
  (rf/dispatch [:load-data])
  (rdom/render [app] (.getElementById js/document "app")))

(defn ^:dev/after-load reload! []
  (println "Hot reloading...")
  (rdom/render [app] (.getElementById js/document "app")))
