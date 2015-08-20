(ns memento.db.memory
  (:require [environ.core :refer [env]]
            [clj-time.format :as tf]
            [clj-time.coerce :as tc]
            [clojure.string :as s]
            [memento.db.core :as db]
            [numergent.utils :refer [remove-html clean-memory-text]]
            [clojure.java.jdbc :as jdbc]
            [clj-time.coerce :as c]
            [clj-time.core :as t])
  (:import (java.util Date UUID)))

(defn now [] (Date.))
(def result-limit 10)


(defn format-created
  "Receives a collection of memories and formats the create date to a string"
  [memories]
  (assoc memories :results
                  (map #(assoc % :created (tf/unparse (tf/formatters :date-hour-minute)
                                                      (tc/from-date (:created %))))
                       (:results memories))))


(def open-duration (* 24 60 60 1000))


(defn set-memory-status
  "Associates a :status with a memory depending on if it's still considered open."
  [memory]
  (let [millis (t/in-millis (t/interval (c/from-date (:created memory)) (t/now)))]
    (assoc memory :status (if (< millis open-duration) :open :closed))
    ))


(defn load-memory
  "Loads a memory by its id"
  [^UUID id]
  (first (db/run db/get-thought-by-id {:id id})))

(defn create-memory!
  "Saves a new memory, after removing HTML tags from the thought."
  [memory]
  (jdbc/with-db-transaction [trans-conn @db/conn]
    (let [refine-id (:refine_id memory)
          refined   (if refine-id (first (db/run db/get-thought-by-id {:id refine-id} trans-conn)))
          root-id   (or (:root_id refined) refine-id)
          item      (->
                      (assoc memory :created (now)
                                  :username (s/lower-case (:username memory))
                                  :refine_id refine-id
                                  :root_id root-id)
                      clean-memory-text)]
      (if refined
        (db/run db/make-root! {:id root-id} trans-conn))
      (db/run db/create-thought<! item trans-conn)
      )))


(defn update-memory!
  "Updates a memory, after removing HTML tags from the thought. It will only
  let you update the text itself, no other values are changed. Only memories
  considered open can be updated."
  [memory]
  (let [current (set-memory-status (first (db/run db/get-thought-by-id memory)))]
    (if (= :open (:status current))
      (db/run db/update-thought<! (clean-memory-text memory))
      {}
      )))


(defn query-memories
  "Queries for a user's memories"
  ([username]
   (query-memories username ""))
  ([^String username ^String query-str]
   (query-memories username query-str 0))
  ([^String username ^String query-str ^Integer offset]
   (let [query-str (-> (or query-str "")
                       (s/replace #"[,.;:]" " ")                      ; Consider commas whitespace
                       (s/replace #"[$!&=\-\*|%&^]" "")               ; Remove characters which could cause it to barf
                       s/trim
                       (s/replace #"\s+" "|")                         ; Replace white space sequences with a single or operator
                       )
         params    {:limit    result-limit
                    :offset   offset
                    :username username
                    ;; Query won't be used in the case of get-thoughts, but bind it on let
                    ;; since we'll need it twice on search.
                    :query    query-str}
         total     (if (empty? query-str)
                     (-> (db/run db/get-thought-count params) first :count)
                     (-> (db/run db/search-thought-count params) first :count))
         results   (if (empty? query-str)
                     (db/run db/get-thoughts params)
                     (db/run db/search-thoughts params))
         ]
     {:total   total
      :pages   (int (Math/ceil (/ total result-limit)))
      :results (map set-memory-status results)}
     )))

(defn query-memory-thread
  "Returns a list with all the memories belonging to a root id"
  [id]
  (->> (db/run db/get-thread-by-root-id {:id id})
       (map set-memory-status)))

