(ns logicflow.front.views.trace
  "Trace view - visualize the backtracking search tree."
  (:require [re-frame.core :as rf]
            [clojure.string :as str]))

(defn build-tree-hierarchy
  "Build a hierarchical tree structure from flat nodes with parent references."
  [nodes]
  (let [_nodes-by-id (into {} (map (juxt :id identity) nodes))
        root-nodes (filter #(nil? (:parent %)) nodes)
        children-map (group-by :parent nodes)]
    (letfn [(build-node [node]
              (let [children (get children-map (:id node) [])]
                (assoc node :children (mapv build-node children))))]
      (mapv build-node root-nodes))))

(defn search-tree-node
  "Render a single node in the search tree."
  [node level]
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

(defn search-tree-visualization
  "Render the complete search tree."
  [tree]
  (let [nodes (:nodes tree)
        hierarchy (build-tree-hierarchy nodes)]
    [:div.search-tree
     (if (empty? hierarchy)
       [:div.empty-tree "Run a query with tracing enabled to see the search tree."]
       [:div.tree-root
        (for [root-node hierarchy]
          ^{:key (:id root-node)}
          [search-tree-node root-node 0])])]))

(defn trace-view
  "View for visualizing the backtracking search tree."
  []
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