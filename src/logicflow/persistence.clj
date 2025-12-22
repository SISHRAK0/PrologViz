(ns logicflow.persistence
  "Persistence layer for saving/loading knowledge bases.
   Supports EDN, JSON formats and includes versioning."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [logicflow.kb :as kb])
  (:import [java.time Instant]
           [java.io File]))

;; ============================================================================
;; File Format Versioning
;; ============================================================================

(def current-version "1.0.0")

(defn wrap-with-metadata
  "Wrap KB data with metadata for versioning."
  [data]
  {:version current-version
   :format :logicflow-kb
   :created-at (str (Instant/now))
   :data data})

(defn validate-format
  "Validate loaded data format."
  [data]
  (and (map? data)
       (= :logicflow-kb (:format data))
       (:data data)))

;; ============================================================================
;; EDN Format (Native Clojure)
;; ============================================================================

(defn save-kb-edn!
  "Save knowledge base to EDN file."
  [filepath]
  (let [data (wrap-with-metadata (kb/export-kb))
        file (io/file filepath)]
    (io/make-parents file)
    (spit file (pr-str data))
    {:status :ok
     :path (.getAbsolutePath file)
     :facts-count (:total-facts (kb/get-stats))
     :rules-count (:total-rules (kb/get-stats))}))

(defn load-kb-edn!
  "Load knowledge base from EDN file."
  [filepath]
  (let [file (io/file filepath)]
    (if (.exists file)
      (let [content (slurp file)
            data (edn/read-string content)]
        (if (validate-format data)
          (do
            (kb/import-kb! (:data data))
            {:status :ok
             :version (:version data)
             :created-at (:created-at data)
             :facts-count (:total-facts (kb/get-stats))
             :rules-count (:total-rules (kb/get-stats))})
          {:status :error
           :message "Invalid file format"}))
      {:status :error
       :message (str "File not found: " filepath)})))

;; ============================================================================
;; JSON Format (For external tools)
;; ============================================================================

(defn save-kb-json!
  "Save knowledge base to JSON file."
  [filepath]
  (let [data (wrap-with-metadata (kb/export-kb))
        file (io/file filepath)]
    (io/make-parents file)
    (spit file (json/generate-string data {:pretty true}))
    {:status :ok
     :path (.getAbsolutePath file)}))

(defn load-kb-json!
  "Load knowledge base from JSON file."
  [filepath]
  (let [file (io/file filepath)]
    (if (.exists file)
      (let [content (slurp file)
            data (json/parse-string content true)]
        (if (validate-format data)
          (do
            (kb/import-kb! (:data data))
            {:status :ok
             :version (:version data)})
          {:status :error
           :message "Invalid file format"}))
      {:status :error
       :message (str "File not found: " filepath)})))

;; ============================================================================
;; Auto-detect Format
;; ============================================================================

(defn detect-format
  "Detect file format from extension."
  [filepath]
  (let [ext (-> filepath str (.toLowerCase))]
    (cond
      (.endsWith ext ".edn") :edn
      (.endsWith ext ".json") :json
      :else :edn)))

(defn save-kb!
  "Save knowledge base, auto-detecting format from extension."
  [filepath]
  (case (detect-format filepath)
    :edn (save-kb-edn! filepath)
    :json (save-kb-json! filepath)))

(defn load-kb!
  "Load knowledge base, auto-detecting format from extension."
  [filepath]
  (case (detect-format filepath)
    :edn (load-kb-edn! filepath)
    :json (load-kb-json! filepath)))

;; ============================================================================
;; Prolog-style Format (for compatibility)
;; ============================================================================

(defn- format-term
  "Format a term in Prolog syntax."
  [term]
  (cond
    (keyword? term) (name term)
    (string? term) (str "\"" term "\"")
    (sequential? term) (str "[" (clojure.string/join ", " (map format-term term)) "]")
    :else (str term)))

(defn- format-fact
  "Format a fact in Prolog syntax."
  [predicate args]
  (str (name predicate) "(" (clojure.string/join ", " (map format-term args)) ")."))

(defn- format-rule
  "Format a rule in Prolog syntax."
  [predicate head body]
  (let [head-str (str (name predicate) "(" (clojure.string/join ", " (map format-term head)) ")")
        body-str (clojure.string/join ", " 
                   (map (fn [[p & args]]
                          (str (name p) "(" (clojure.string/join ", " (map format-term args)) ")"))
                        body))]
    (str head-str " :- " body-str ".")))

(defn export-prolog
  "Export knowledge base to Prolog-compatible syntax."
  []
  (let [facts (kb/get-all-facts)
        rules (kb/get-all-rules)]
    (str
     "%% LogicFlow Knowledge Base Export\n"
     "%% Generated: " (str (Instant/now)) "\n\n"
     "%% Facts\n"
     (clojure.string/join "\n"
       (for [[pred fact-set] facts
             args fact-set]
         (format-fact pred args)))
     "\n\n%% Rules\n"
     (clojure.string/join "\n"
       (for [[pred rule-list] rules
             {:keys [head body]} rule-list]
         (format-rule pred head body))))))

(defn save-prolog!
  "Save knowledge base to Prolog file."
  [filepath]
  (let [file (io/file filepath)]
    (io/make-parents file)
    (spit file (export-prolog))
    {:status :ok
     :path (.getAbsolutePath file)}))

;; ============================================================================
;; Backup/Restore
;; ============================================================================

(def backup-dir "backups")

(defn create-backup!
  "Create a timestamped backup of the knowledge base."
  []
  (let [timestamp (-> (Instant/now) str (.replace ":" "-"))
        filename (str backup-dir "/kb-backup-" timestamp ".edn")]
    (save-kb-edn! filename)))

(defn list-backups
  "List all available backups."
  []
  (let [dir (io/file backup-dir)]
    (if (.exists dir)
      (->> (.listFiles dir)
           (filter #(.endsWith (.getName %) ".edn"))
           (map (fn [f]
                  {:filename (.getName f)
                   :path (.getAbsolutePath f)
                   :size (.length f)
                   :modified (.lastModified f)}))
           (sort-by :modified >))
      [])))

(defn restore-backup!
  "Restore from a backup file."
  [backup-name]
  (load-kb-edn! (str backup-dir "/" backup-name)))

(defn cleanup-old-backups!
  "Keep only the N most recent backups."
  [keep-count]
  (let [backups (list-backups)
        to-delete (drop keep-count backups)]
    (doseq [{:keys [path]} to-delete]
      (io/delete-file path))
    {:deleted (count to-delete)
     :remaining keep-count}))

;; ============================================================================
;; Import from other formats
;; ============================================================================

(defn parse-prolog-fact
  "Parse a simple Prolog fact line."
  [line]
  (when-let [[_ pred args-str] (re-matches #"(\w+)\(([^)]*)\)\." line)]
    {:predicate (keyword pred)
     :args (mapv (fn [s]
                   (let [s (clojure.string/trim s)]
                     (cond
                       (re-matches #"\d+" s) (parse-long s)
                       (re-matches #"\".*\"" s) (subs s 1 (dec (count s)))
                       :else (keyword s))))
                 (clojure.string/split args-str #","))}))

(defn import-prolog-facts!
  "Import simple Prolog facts from a file."
  [filepath]
  (let [lines (clojure.string/split-lines (slurp filepath))
        facts (->> lines
                   (remove #(or (clojure.string/blank? %)
                               (clojure.string/starts-with? % "%")))
                   (keep parse-prolog-fact))]
    (doseq [{:keys [predicate args]} facts]
      (kb/assert-fact! predicate args))
    {:status :ok
     :imported (count facts)}))

