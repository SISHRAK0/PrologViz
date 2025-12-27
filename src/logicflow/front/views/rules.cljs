(ns logicflow.front.views.rules
  "Rules view - display and manage rules in the knowledge base."
  (:require [re-frame.core :as rf]))

(defn rules-view
  "View for browsing rules by predicate."
  []
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