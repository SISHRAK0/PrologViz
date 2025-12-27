(ns logicflow.front.views.repl
  "REPL view - interactive Clojure REPL for the knowledge base."
  (:require [re-frame.core :as rf]))

(defn repl-view
  "Interactive REPL for executing Clojure code."
  []
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