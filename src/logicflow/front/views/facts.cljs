(ns logicflow.front.views.facts
  "Facts view - display and manage facts in the knowledge base."
  (:require [re-frame.core :as rf]))

(defn facts-view
  "View for browsing facts by predicate."
  []
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