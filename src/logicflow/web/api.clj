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

(defn success [data]
  (response/response {:status "success" :data data}))

(defn error
  ([msg] (error msg 400))
  ([msg status]
   (-> (response/response {:status "error" :message msg})
       (response/status status))))

(defn get-facts [request]
  (let [predicate (get-in request [:params :predicate])]
    (if predicate
      (success {:predicate predicate
                :facts (kb/get-facts (keyword predicate))})
      (success {:facts (kb/get-all-facts)}))))

(defn add-fact [request]
  (let [{:keys [predicate args]} (:body request)]
    (if (and predicate args)
      (let [pred-kw (keyword predicate)
            result (kb/assert-fact! pred-kw (vec args))]
        (ws/broadcast! {:type :fact-added :predicate pred-kw :args args})
        (success result))
      (error "Missing predicate or args"))))

(defn remove-fact [request]
  (let [{:keys [predicate args]} (:body request)]
    (if (and predicate args)
      (let [pred-kw (keyword predicate)
            result (kb/retract-fact! pred-kw (vec args))]
        (ws/broadcast! {:type :fact-removed :predicate pred-kw :args args})
        (success result))
      (error "Missing predicate or args"))))

(defn get-rules [request]
  (let [predicate (get-in request [:params :predicate])]
    (if predicate
      (success {:predicate predicate
                :rules (kb/get-rules (keyword predicate))})
      (success {:rules (kb/get-all-rules)}))))

(defn add-rule [request]
  (let [{:keys [predicate head body]} (:body request)]
    (if (and predicate head body)
      (let [pred-kw (keyword predicate)
            body-terms (mapv (fn [term]
                              (if (sequential? term)
                                (cons (keyword (first term)) (rest term))
                                term))
                            body)
            result (kb/add-rule! pred-kw head body-terms)]
        (ws/broadcast! {:type :rule-added :predicate pred-kw :head head :body body})
        (success result))
      (error "Missing predicate, head, or body"))))

(defn parse-query-arg [arg]
  (cond
    (keyword? arg) arg
    (symbol? arg) arg
    (and (string? arg) (str/starts-with? arg "?")) (symbol arg)
    (string? arg) (keyword arg)
    :else arg))

(defn parse-query-term [term]
  (if (sequential? term)
    (cons (keyword (first term)) 
          (map parse-query-arg (rest term)))
    term))

(defn execute-query [request]
  (let [{:keys [goals limit trace]} (:body request)]
    (if (and goals (sequential? goals))
      (try
        (let [parsed-goals (mapv parse-query-term goals)
              query-opts (cond-> []
                           limit (into [:limit limit])
                           trace (into [:trace? true]))
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

(defn eval-repl [request]
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

(defn get-history [request]
  (let [limit (some-> (get-in request [:params :limit]) parse-long)]
    (success {:history (if limit (kb/get-history limit) (kb/get-history))})))

(defn get-stats [_request]
  (success (merge (kb/get-stats) {:tabling (tabling/get-table-stats)})))

(defn get-trace [_request]
  (success {:log (trace/get-trace-log)
            :tree (trace/export-trace-tree)
            :stats (trace/trace-stats)}))

(defn clear-trace [_request]
  (trace/clear-trace!)
  (success {:message "Trace cleared"}))

(defn clear-kb [_request]
  (kb/clear-kb!)
  (tabling/clear-tables!)
  (trace/clear-trace!)
  (ws/broadcast! {:type :kb-cleared})
  (success {:message "Knowledge base cleared"}))

(defn load-example [request]
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
      "nqueens" (do (examples/load-nqueens-example!)
                    (ws/broadcast! {:type :example-loaded :example "nqueens"})
                    (success {:message "N-Queens example loaded"}))
      "einstein" (do (examples/load-einstein-puzzle!)
                     (ws/broadcast! {:type :example-loaded :example "einstein"})
                     (success {:message "Einstein's Riddle loaded"}))
      "sudoku" (do (examples/load-sudoku-example!)
                   (ws/broadcast! {:type :example-loaded :example "sudoku"})
                   (success {:message "Sudoku example loaded"}))
      (error (str "Unknown example: " example)))))

(defn export-kb [_request]
  (success (kb/export-kb)))

(defn import-kb [request]
  (let [data (:body request)]
    (if (and (:facts data) (:rules data))
      (do
        (kb/import-kb! data)
        (ws/broadcast! {:type :kb-imported})
        (success {:message "Knowledge base imported"}))
      (error "Invalid import data"))))

(defn save-kb [request]
  (let [filename (get-in request [:body :filename] "kb.edn")]
    (try
      (success (persist/save-kb! filename))
      (catch Exception e
        (error (str "Save error: " (.getMessage e)))))))

(defn load-kb-file [request]
  (let [filename (get-in request [:body :filename])]
    (if filename
      (try
        (let [result (persist/load-kb! filename)]
          (ws/broadcast! {:type :kb-loaded :filename filename})
          (success result))
        (catch Exception e
          (error (str "Load error: " (.getMessage e)))))
      (error "Missing filename"))))

(defn export-prolog [_request]
  {:status 200
   :headers {"Content-Type" "text/plain"
             "Content-Disposition" "attachment; filename=\"kb.pl\""}
   :body (persist/export-prolog)})

(defn create-backup [_request]
  (success (persist/create-backup!)))

(defn list-backups [_request]
  (success {:backups (persist/list-backups)}))

(defn get-table-info [_request]
  (success (tabling/table-info)))

(defn clear-tables [_request]
  (tabling/clear-tables!)
  (success {:message "Tables cleared"}))

(defn list-examples [_request]
  (success {:examples
            [{:id "family" :name "Family Relations" :description "Basic family tree"}
             {:id "animal" :name "Animal Classification" :description "Simple animal types"}
             {:id "extended-family" :name "Extended Family" :description "Family with dates and more relations"}
             {:id "expert" :name "Animal Expert" :description "Animal identification expert system"}
             {:id "graph" :name "Graph Traversal" :description "Path finding in graphs"}
             {:id "database" :name "Database Queries" :description "Relational database example"}
             {:id "nqueens" :name "N-Queens" :description "Classic N-Queens puzzle"}
             {:id "einstein" :name "Einstein's Riddle" :description "Who owns the fish?"}
             {:id "sudoku" :name "Sudoku" :description "Sudoku puzzle solver"}]}))

;; Spy Points API
(defn get-spy-points [_request]
  (success {:spy-points (vec (trace/get-spy-points))
            :spy-log (trace/get-spy-log)
            :stats (trace/spy-stats)}))

(defn add-spy-point [request]
  (let [predicate (get-in request [:body :predicate])]
    (if predicate
      (do
        (trace/spy! (keyword predicate))
        (ws/broadcast! {:type :spy-added :predicate predicate})
        (success {:message (str "Spy point added on: " predicate)
                  :spy-points (vec (trace/get-spy-points))}))
      (error "Missing predicate"))))

(defn remove-spy-point [request]
  (let [predicate (get-in request [:body :predicate])]
    (if predicate
      (do
        (trace/nospy! (keyword predicate))
        (ws/broadcast! {:type :spy-removed :predicate predicate})
        (success {:message (str "Spy point removed from: " predicate)
                  :spy-points (vec (trace/get-spy-points))}))
      (error "Missing predicate"))))

(defn clear-spy-points [_request]
  (trace/nospy-all!)
  (trace/clear-spy-log!)
  (ws/broadcast! {:type :spy-cleared})
  (success {:message "All spy points cleared"}))

(defn get-spy-log [_request]
  (success {:log (trace/get-spy-log)
            :stats (trace/spy-stats)}))

(defn clear-spy-log [_request]
  (trace/clear-spy-log!)
  (success {:message "Spy log cleared"}))

;; Puzzle Solvers API
(defn solve-nqueens-api [request]
  (let [n (get-in request [:body :n] 8)]
    (try
      (let [solutions (take 10 (examples/solve-nqueens n))]
        (success {:n n
                  :solutions (vec solutions)
                  :count (count solutions)}))
      (catch Exception e
        (error (str "Solve error: " (.getMessage e)))))))

(defn solve-sudoku-api [request]
  (let [puzzle (get-in request [:body :puzzle])]
    (if puzzle
      (try
        (let [solution (examples/solve-sudoku puzzle)]
          (success {:puzzle puzzle
                    :solution solution
                    :solved (some? solution)}))
        (catch Exception e
          (error (str "Solve error: " (.getMessage e)))))
      (error "Missing puzzle"))))

(defn solve-einstein-api [_request]
  (success (examples/solve-einstein)))
