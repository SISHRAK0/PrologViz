(ns logicflow.web.api
  "REST API handlers for LogicFlow."
  (:require [ring.util.response :as response]
            [clojure.string :as str]
            [logicflow.kb :as kb]
            [logicflow.core :as core]
            [logicflow.trace :as trace]
            [logicflow.persistence :as persist]
            [logicflow.tabling :as tabling]
            [logicflow.examples :as examples]
            [logicflow.web.ws :as ws]))

;; ============================================================================
;; Response Helpers
;; ============================================================================

(defn success
  "Create a success response."
  [data]
  (response/response {:status "success" :data data}))

(defn error
  "Create an error response."
  ([msg] (error msg 400))
  ([msg status]
   (-> (response/response {:status "error" :message msg})
       (response/status status))))

;; ============================================================================
;; Facts API
;; ============================================================================

(defn get-facts
  "GET /api/facts - Get all facts or facts for a predicate."
  [request]
  (let [predicate (get-in request [:params :predicate])]
    (if predicate
      (success {:predicate predicate
                :facts (kb/get-facts (keyword predicate))})
      (success {:facts (kb/get-all-facts)}))))

(defn add-fact
  "POST /api/facts - Add a new fact.
   Body: {:predicate \"parent\" :args [\"tom\" \"mary\"]}"
  [request]
  (let [{:keys [predicate args]} (:body request)]
    (if (and predicate args)
      (let [pred-kw (keyword predicate)
            result (kb/assert-fact! pred-kw (vec args))]
        (ws/broadcast! {:type :fact-added
                        :predicate pred-kw
                        :args args})
        (success result))
      (error "Missing predicate or args"))))

(defn remove-fact
  "DELETE /api/facts - Remove a fact.
   Body: {:predicate \"parent\" :args [\"tom\" \"mary\"]}"
  [request]
  (let [{:keys [predicate args]} (:body request)]
    (if (and predicate args)
      (let [pred-kw (keyword predicate)
            result (kb/retract-fact! pred-kw (vec args))]
        (ws/broadcast! {:type :fact-removed
                        :predicate pred-kw
                        :args args})
        (success result))
      (error "Missing predicate or args"))))

;; ============================================================================
;; Rules API
;; ============================================================================

(defn get-rules
  "GET /api/rules - Get all rules or rules for a predicate."
  [request]
  (let [predicate (get-in request [:params :predicate])]
    (if predicate
      (success {:predicate predicate
                :rules (kb/get-rules (keyword predicate))})
      (success {:rules (kb/get-all-rules)}))))

(defn add-rule
  "POST /api/rules - Add a new rule.
   Body: {:predicate \"ancestor\" 
          :head [\"?x\" \"?y\"] 
          :body [[\"parent\" \"?x\" \"?y\"]]}"
  [request]
  (let [{:keys [predicate head body]} (:body request)]
    (if (and predicate head body)
      (let [pred-kw (keyword predicate)
            body-terms (mapv (fn [term]
                              (if (sequential? term)
                                (cons (keyword (first term)) (rest term))
                                term))
                            body)
            result (kb/add-rule! pred-kw head body-terms)]
        (ws/broadcast! {:type :rule-added
                        :predicate pred-kw
                        :head head
                        :body body})
        (success result))
      (error "Missing predicate, head, or body"))))

;; ============================================================================
;; Query API
;; ============================================================================

(defn parse-query-arg
  "Parse a query argument from the API format.
   Converts strings to keywords unless they start with ? (variables)."
  [arg]
  (cond
    ;; Already a keyword or symbol
    (keyword? arg) arg
    (symbol? arg) arg
    ;; Variable string (starts with ?)
    (and (string? arg) (str/starts-with? arg "?"))
    (symbol arg)
    ;; Regular string -> keyword (for matching facts)
    (string? arg) (keyword arg)
    ;; Keep numbers and other types as-is
    :else arg))

(defn parse-query-term
  "Parse a query term from the API format."
  [term]
  (if (sequential? term)
    (cons (keyword (first term)) 
          (map parse-query-arg (rest term)))
    term))

(defn execute-query
  "POST /api/query - Execute a query.
   Body: {:goals [[\"grandparent\" \"?who\" \"ann\"]], :trace true, :limit 100}"
  [request]
  (let [{:keys [goals limit trace]} (:body request)]
    (if (and goals (sequential? goals))
      (try
        (let [parsed-goals (mapv parse-query-term goals)
              ;; Build query options
              query-opts (cond-> []
                           limit (into [:limit limit])
                           trace (into [:trace? true]))
              ;; Execute query
              results (apply kb/query parsed-goals query-opts)
              trace-data (when trace
                           {:log (trace/get-trace-log)
                            :tree (trace/export-trace-tree)
                            :stats (trace/trace-stats)})]
          (ws/broadcast! {:type :query-executed
                          :goals parsed-goals
                          :result-count (count results)})
          (success (cond-> {:goals parsed-goals
                            :results (vec results)
                            :count (count results)}
                     trace (assoc :trace trace-data))))
        (catch Exception e
          (error (str "Query error: " (.getMessage e)))))
      (error "Missing or invalid goals"))))

;; ============================================================================
;; REPL API (for browser REPL)
;; ============================================================================

(defn eval-repl
  "POST /api/repl - Evaluate a Clojure/Prolog expression.
   Body: {:code \"(query (parent ?x ?y))\"}"
  [request]
  (let [{:keys [code]} (:body request)]
    (if code
      (try
        (let [result (binding [*ns* (find-ns 'logicflow.core)]
                       (load-string (str "(do (require '[logicflow.core :refer :all])"
                                        "(require '[logicflow.kb :as kb])"
                                        "(require '[logicflow.builtins :as b])"
                                        code ")")))]
          (success {:result (pr-str result)
                    :type (str (type result))}))
        (catch Exception e
          (error (str "Eval error: " (.getMessage e)))))
      (error "Missing code"))))

;; ============================================================================
;; History API
;; ============================================================================

(defn get-history
  "GET /api/history - Get transaction history."
  [request]
  (let [limit (some-> (get-in request [:params :limit])
                      parse-long)]
    (success {:history (if limit
                         (kb/get-history limit)
                         (kb/get-history))})))

;; ============================================================================
;; Stats API
;; ============================================================================

(defn get-stats
  "GET /api/stats - Get knowledge base statistics."
  [_request]
  (success (merge (kb/get-stats)
                  {:tabling (tabling/get-table-stats)})))

;; ============================================================================
;; Trace API
;; ============================================================================

(defn get-trace
  "GET /api/trace - Get current trace data."
  [_request]
  (success {:log (trace/get-trace-log)
            :tree (trace/export-trace-tree)
            :stats (trace/trace-stats)}))

(defn clear-trace
  "POST /api/trace/clear - Clear trace data."
  [_request]
  (trace/clear-trace!)
  (success {:message "Trace cleared"}))

;; ============================================================================
;; Management API
;; ============================================================================

(defn clear-kb
  "POST /api/clear - Clear the knowledge base."
  [_request]
  (kb/clear-kb!)
  (tabling/clear-tables!)
  (trace/clear-trace!)
  (ws/broadcast! {:type :kb-cleared})
  (success {:message "Knowledge base cleared"}))

(defn load-example
  "POST /api/load-example - Load an example knowledge base.
   Body: {:example \"family\"}"
  [request]
  (let [example (get-in request [:body :example])]
    (case example
      "family" (do (core/load-family-example!)
                   (ws/broadcast! {:type :example-loaded :example "family"})
                   (success {:message "Family example loaded"}))
      "animal" (do (core/load-animal-example!)
                   (ws/broadcast! {:type :example-loaded :example "animal"})
                   (success {:message "Animal example loaded"}))
      "extended-family" (do (examples/load-extended-family!)
                            (ws/broadcast! {:type :example-loaded :example "extended-family"})
                            (success {:message "Extended family example loaded"}))
      "expert" (do (examples/load-animal-expert!)
                   (ws/broadcast! {:type :example-loaded :example "expert"})
                   (success {:message "Animal expert system loaded"}))
      "graph" (do (examples/load-graph-example!)
                  (ws/broadcast! {:type :example-loaded :example "graph"})
                  (success {:message "Graph example loaded"}))
      "database" (do (examples/load-database-example!)
                     (ws/broadcast! {:type :example-loaded :example "database"})
                     (success {:message "Database example loaded"}))
      (error (str "Unknown example: " example)))))

;; ============================================================================
;; Persistence API
;; ============================================================================

(defn export-kb
  "GET /api/export - Export the knowledge base."
  [_request]
  (success (kb/export-kb)))

(defn import-kb
  "POST /api/import - Import a knowledge base.
   Body: {:facts {...} :rules {...}}"
  [request]
  (let [data (:body request)]
    (if (and (:facts data) (:rules data))
      (do
        (kb/import-kb! data)
        (ws/broadcast! {:type :kb-imported})
        (success {:message "Knowledge base imported"}))
      (error "Invalid import data"))))

(defn save-kb
  "POST /api/save - Save KB to file.
   Body: {:filename \"kb.edn\"}"
  [request]
  (let [filename (get-in request [:body :filename] "kb.edn")]
    (try
      (success (persist/save-kb! filename))
      (catch Exception e
        (error (str "Save error: " (.getMessage e)))))))

(defn load-kb-file
  "POST /api/load-file - Load KB from file.
   Body: {:filename \"kb.edn\"}"
  [request]
  (let [filename (get-in request [:body :filename])]
    (if filename
      (try
        (let [result (persist/load-kb! filename)]
          (ws/broadcast! {:type :kb-loaded :filename filename})
          (success result))
        (catch Exception e
          (error (str "Load error: " (.getMessage e)))))
      (error "Missing filename"))))

(defn export-prolog
  "GET /api/export-prolog - Export KB in Prolog format."
  [_request]
  {:status 200
   :headers {"Content-Type" "text/plain"
             "Content-Disposition" "attachment; filename=\"kb.pl\""}
   :body (persist/export-prolog)})

(defn create-backup
  "POST /api/backup - Create a backup."
  [_request]
  (success (persist/create-backup!)))

(defn list-backups
  "GET /api/backups - List available backups."
  [_request]
  (success {:backups (persist/list-backups)}))

;; ============================================================================
;; Tabling API
;; ============================================================================

(defn get-table-info
  "GET /api/tabling - Get tabling info."
  [_request]
  (success (tabling/table-info)))

(defn clear-tables
  "POST /api/tabling/clear - Clear all tables."
  [_request]
  (tabling/clear-tables!)
  (success {:message "Tables cleared"}))

;; ============================================================================
;; Examples API
;; ============================================================================

(defn list-examples
  "GET /api/examples - List available examples."
  [_request]
  (success {:examples
            [{:id "family" :name "Family Relations" :description "Basic family tree"}
             {:id "animal" :name "Animal Classification" :description "Simple animal types"}
             {:id "extended-family" :name "Extended Family" :description "Family with dates and more relations"}
             {:id "expert" :name "Animal Expert" :description "Animal identification expert system"}
             {:id "graph" :name "Graph Traversal" :description "Path finding in graphs"}
             {:id "database" :name "Database Queries" :description "Relational database example"}]}))
