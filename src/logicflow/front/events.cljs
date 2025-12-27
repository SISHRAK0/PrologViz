(ns logicflow.front.events
  "Re-frame events for LogicFlow UI."
  (:require [re-frame.core :as rf]
            [ajax.core :as ajax]
            [day8.re-frame.http-fx]
            [clojure.string :as str]
            [cljs.reader :as reader]
            [logicflow.front.db :as db]))

;; ============================================================================
;; Initialization
;; ============================================================================

(rf/reg-event-db
 :initialize
 (fn [_ _]
   db/default-db))

;; ============================================================================
;; Data Loading Events
;; ============================================================================

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
 (fn [{:keys [_db]} _]
   {:http-xhrio {:method :get
                 :uri (str db/api-base "/api/facts")
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:facts-loaded]
                 :on-failure [:api-error]}}))

(rf/reg-event-db
 :facts-loaded
 (fn [db [_ response]]
   (assoc db :facts (get-in response [:data :facts]) :loading false)))

(rf/reg-event-fx
 :fetch-rules
 (fn [{:keys [_db]} _]
   {:http-xhrio {:method :get
                 :uri (str db/api-base "/api/rules")
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:rules-loaded]
                 :on-failure [:api-error]}}))

(rf/reg-event-db
 :rules-loaded
 (fn [db [_ response]]
   (assoc db :rules (get-in response [:data :rules]))))

(rf/reg-event-fx
 :fetch-stats
 (fn [{:keys [_db]} _]
   {:http-xhrio {:method :get
                 :uri (str db/api-base "/api/stats")
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:stats-loaded]
                 :on-failure [:api-error]}}))

(rf/reg-event-db
 :stats-loaded
 (fn [db [_ response]]
   (assoc db :stats (:data response))))

(rf/reg-event-fx
 :fetch-history
 (fn [{:keys [_db]} _]
   {:http-xhrio {:method :get
                 :uri (str db/api-base "/api/history?limit=50")
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:history-loaded]
                 :on-failure [:api-error]}}))

(rf/reg-event-db
 :history-loaded
 (fn [db [_ response]]
   (assoc db :history (get-in response [:data :history]))))

(rf/reg-event-fx
 :fetch-examples
 (fn [{:keys [_db]} _]
   {:http-xhrio {:method :get
                 :uri (str db/api-base "/api/examples")
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:examples-loaded]
                 :on-failure [:api-error]}}))

(rf/reg-event-db
 :examples-loaded
 (fn [db [_ response]]
   (assoc db :examples (get-in response [:data :examples]))))

;; ============================================================================
;; Error Handling
;; ============================================================================

(rf/reg-event-db
 :api-error
 (fn [db [_ error]]
   (assoc db :error (str "API Error: " (:status-text error)) :loading false)))

(rf/reg-event-db
 :dismiss-error
 (fn [db _]
   (assoc db :error nil)))

;; ============================================================================
;; Query Events
;; ============================================================================

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
                     :uri (str db/api-base "/api/query")
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

;; ============================================================================
;; REPL Events
;; ============================================================================

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
                     :uri (str db/api-base "/api/repl")
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

;; ============================================================================
;; Facts & Rules Events
;; ============================================================================

(rf/reg-event-fx
 :add-fact
 (fn [{:keys [_db]} [_ predicate args]]
   {:http-xhrio {:method :post
                 :uri (str db/api-base "/api/facts")
                 :params {:predicate predicate :args args}
                 :format (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:fact-added]
                 :on-failure [:api-error]}}))

(rf/reg-event-fx
 :fact-added
 (fn [{:keys [_db]} _]
   {:dispatch [:load-data]}))

(rf/reg-event-fx
 :load-example
 (fn [{:keys [db]} [_ example]]
   {:db (assoc db :loading true)
    :http-xhrio {:method :post
                 :uri (str db/api-base "/api/load-example")
                 :params {:example example}
                 :format (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:example-loaded]
                 :on-failure [:api-error]}}))

(rf/reg-event-fx
 :example-loaded
 (fn [{:keys [_db]} _]
   {:dispatch [:load-data]}))

(rf/reg-event-fx
 :clear-kb
 (fn [{:keys [_db]} _]
   {:http-xhrio {:method :post
                 :uri (str db/api-base "/api/clear")
                 :format (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:kb-cleared]
                 :on-failure [:api-error]}}))

(rf/reg-event-fx
 :kb-cleared
 (fn [{:keys [_db]} _]
   {:dispatch [:load-data]}))

;; ============================================================================
;; Navigation Events
;; ============================================================================

(rf/reg-event-db
 :select-predicate
 (fn [db [_ pred]]
   (assoc db :selected-predicate pred)))

(rf/reg-event-db
 :set-view
 (fn [db [_ view]]
   (assoc db :view view)))

;; ============================================================================
;; WebSocket Events
;; ============================================================================

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
 (fn [{:keys [_db]} [_ message]]
   (case (:type message)
     "facts-changed" {:dispatch [:fetch-facts]}
     "rules-changed" {:dispatch [:fetch-rules]}
     :fact-added {:dispatch [:load-data]}
     :rule-added {:dispatch [:load-data]}
     :kb-cleared {:dispatch [:load-data]}
     :example-loaded {:dispatch [:load-data]}
     {})))

;; ============================================================================
;; Spy Points Events
;; ============================================================================

(rf/reg-event-db
 :set-spy-input
 (fn [db [_ value]]
   (assoc db :spy-input value)))

(rf/reg-event-fx
 :fetch-spy-points
 (fn [{:keys [_db]} _]
   {:http-xhrio {:method :get
                 :uri (str db/api-base "/api/spy")
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
                     :uri (str db/api-base "/api/spy")
                     :params {:predicate pred}
                     :format (ajax/json-request-format)
                     :response-format (ajax/json-response-format {:keywords? true})
                     :on-success [:spy-point-added]
                     :on-failure [:api-error]}}))))

(rf/reg-event-fx
 :spy-point-added
 (fn [{:keys [_db]} _]
   {:dispatch [:fetch-spy-points]}))

(rf/reg-event-fx
 :remove-spy-point
 (fn [{:keys [_db]} [_ pred]]
   {:http-xhrio {:method :delete
                 :uri (str db/api-base "/api/spy")
                 :params {:predicate pred}
                 :format (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:spy-point-removed]
                 :on-failure [:api-error]}}))

(rf/reg-event-fx
 :spy-point-removed
 (fn [{:keys [_db]} _]
   {:dispatch [:fetch-spy-points]}))

(rf/reg-event-fx
 :clear-spy-points
 (fn [{:keys [_db]} _]
   {:http-xhrio {:method :post
                 :uri (str db/api-base "/api/spy/clear")
                 :format (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:spy-points-cleared]
                 :on-failure [:api-error]}}))

(rf/reg-event-fx
 :spy-points-cleared
 (fn [{:keys [_db]} _]
   {:dispatch [:fetch-spy-points]}))

;; ============================================================================
;; Puzzle Events
;; ============================================================================

(rf/reg-event-db
 :set-nqueens-n
 (fn [db [_ n]]
   (assoc db :nqueens-n n)))

(rf/reg-event-fx
 :solve-nqueens
 (fn [{:keys [db]} _]
   {:db (assoc db :loading true :nqueens-solutions [])
    :http-xhrio {:method :post
                 :uri (str db/api-base "/api/solve/nqueens")
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
                 :uri (str db/api-base "/api/solve/einstein")
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
                 :uri (str db/api-base "/api/solve/sudoku")
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