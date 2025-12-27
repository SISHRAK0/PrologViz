(ns logicflow.front.views.dashboard
  "Dashboard view - main overview of the knowledge base."
  (:require [re-frame.core :as rf]
            [logicflow.front.components.common :as c]))

(defn dashboard-view
  "Main dashboard with statistics and quick actions."
  []
  (let [stats @(rf/subscribe [:stats])
        facts @(rf/subscribe [:facts])
        rules @(rf/subscribe [:rules])
        examples @(rf/subscribe [:examples])]
    [:div.dashboard
     [:h2 "Dashboard"]
     
     [:div.stats-grid
      [c/stats-card "Total Facts" (or (:total-facts stats) 0) "📝"]
      [c/stats-card "Total Rules" (or (:total-rules stats) 0) "⚙️"]
      [c/stats-card "Predicates" (count (or (:predicates stats) [])) "🏷️"]
      [c/stats-card "Queries" (or (:queries stats) 0) "🔍"]]
     
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