(ns logicflow.front.views.spy
  "Spy view - debug predicates with spy points."
  (:require [re-frame.core :as rf]))

(defn spy-view
  "View for managing spy points and viewing spy logs."
  []
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