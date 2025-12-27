(ns logicflow.front.views.query
  "Query view - execute queries against the knowledge base."
  (:require [re-frame.core :as rf]
            [logicflow.front.components.common :as c]))

(defn query-view
  "View for executing and viewing query results."
  []
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
        loading [c/loading-spinner]
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