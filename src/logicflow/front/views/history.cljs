(ns logicflow.front.views.history
  "History view - transaction history of the knowledge base."
  (:require [re-frame.core :as rf]))

(defn history-view
  "View for displaying transaction history."
  []
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