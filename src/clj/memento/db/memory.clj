(ns memento.db.memory
  (:require [memento.config :refer [env]]
            [clojure.string :as s]
            [memento.db.core :refer [*db*] :as db]
            [memento.misc.html :refer [remove-html clean-memory-text]]
            [clojure.java.jdbc :as jdbc]
            [clj-time.coerce :as c]
            [clj-time.core :as t])
  (:import (java.util Date UUID)))

(defn now [] (Date.))
(def default-limit 10)

;; TODO Consider making this configurable
(def open-duration (* 24 60 60 1000))


(defn set-status
  "Associates a :status with a memory depending on if it's still considered open."
  [memory]
  (when memory
    (let [millis (t/in-millis (t/interval (c/from-date (:created memory)) (t/now)))]
      (assoc memory :status (if (< millis open-duration) :open :closed)))))


(defn get-by-id
  "Loads a memory by its id and adds a status"
  [^UUID id]
  (set-status
    (db/get-thought-by-id *db* {:id id})))

(defn get-if-owner
  "Returns a thought if owned by a user, or nil otherwise"
  [username id]
  (let [existing (get-by-id id)]
    (when (= username (:username existing))
      existing)))

(defn create!
  "Saves a new memory, after removing HTML tags from the thought."
  [memory]
  (jdbc/with-db-transaction
    [trans-conn *db*]
    (let [refine-id (:follow-id memory)
          refined   (if refine-id (db/get-thought-by-id trans-conn {:id refine-id}))
          root-id   (or (:root-id refined) refine-id)
          item      (clean-memory-text
                      (assoc memory :created (now)
                                    :username (s/lower-case (:username memory))
                                    :follow-id refine-id
                                    :root-id root-id))]
      (if refined
        (db/make-root! trans-conn {:id root-id}))
      (set-status (db/create-thought! trans-conn item)))))


(defn update!
  "Updates a memory, after removing HTML tags from the thought. It will only
  let you update the text itself, no other values are changed. Only memories
  considered open can be updated."
  [memory]
  (let [current (set-status (db/get-thought-by-id *db* memory))]
    (if (= :open (:status current))
      (set-status (db/update-thought! *db* (clean-memory-text memory)))
      {}
      )))


(defn archive!
  "Archives/de-archives a thought. The thought does not need to be open."
  [memory]
  (set-status (db/archive-thought! *db* memory)))


(defn delete!
  "Deletes a memory from the database, only if it's still considered open.
  Will return 1 if the memory was deleted, 0 otherwise."
  [^UUID id]
  (let [current (set-status (db/get-thought-by-id *db* {:id id}))]
    (if (= :open (:status current))
      (jdbc/with-db-transaction
        [trans-conn *db*]
        (db/delete-reminders-for-thought! trans-conn {:id id})
        (db/delete-thought! trans-conn {:id id}))
      0)))


(defn query
  "Queries for a user's memories"
  ([username]
   (query username ""))
  ([^String username ^String query-str]
   (query username query-str 0 false))
  ([^String username ^String query-str ^Integer offset]
   (query username query-str offset false))
  ([^String username ^String query-str ^Integer offset ^Boolean include-archived?
    & {:keys [extra-joins limit] :or {extra-joins nil limit default-limit}}]
   (let [query-str (-> (or query-str "")
                       (s/replace #"[,.;:]" " ")            ; Consider commas whitespace
                       (s/replace #"[$!&=\-\*|%&^]" "")     ; Remove characters which could cause it to barf
                       s/trim
                       (s/replace #"\s+" "|")               ; Replace white space sequences with a single or operator
                       )
         params    {:limit       limit
                    :offset      offset
                    :username    username
                    ;; Query won't be used in the case of get-thoughts, but bind it on let
                    ;; since we'll need it twice on search.
                    :query       query-str
                    :all?        include-archived?
                    :extra-joins extra-joins}
         total     (if (empty? query-str)
                     (:count (db/get-thought-count *db* params))
                     (:count (db/search-thought-count *db* params)))
         results   (->>
                     (if (empty? query-str)
                       (db/get-thoughts *db* params)
                       (db/search-thoughts *db* params))
                     (map #(assoc % :reminders (db/get-active-reminders-for-thought
                                                 (select-keys % [:id :username])))))]
     {:total   total
      :pages   (int (Math/ceil (/ total limit)))
      :results (map set-status results)})))

(defn query-thread
  "Returns a list with all the memories belonging to a root id"
  [id]
  (->> (db/get-thread-by-root-id *db* {:id id})
       (map set-status)
       (map #(assoc % :reminders (db/get-active-reminders-for-thought
                                   (select-keys % [:id :username]))))))
