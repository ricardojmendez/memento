(ns memento.db.memory
  (:require [environ.core :refer [env]]
            [clj-time.format :as tf]
            [clj-time.coerce :as tc]
            [clojure.string :as s]
            [memento.db.core :as db]
            [numergent.utils :refer [remove-html]])
  (:import (java.util Date)))

(defn now [] (Date.))
(def result-limit 10)


(defn format-created
  "Receives a collection of memories and formats the create date to a string"
  [memories]
  (assoc memories :results
                  (map #(assoc % :created (tf/unparse (tf/formatters :date-hour-minute)
                                                      (tc/from-date (:created %))))
                       (:results memories))))

(defn save-memory!
  "Saves a new memory, after removing HTML tags from the thought."
  [memory]
  (let [refine-id (or (:refine_id memory) nil)
        refined   (if refine-id (first (db/get-thought-by-id {:id refine-id})))
        root-id   (or (:root_id refined) refine-id)
        item      (assoc memory :created (now)
                                :username (s/lower-case (:username memory))
                                :thought (remove-html (:thought memory))
                                :refine_id refine-id
                                :root_id root-id)]
    (db/create-thought! item)))

(defn query-memories
  "Queries for a user's memories"
  ([username]
   (query-memories username ""))
  ([^String username ^String query-str]
   (query-memories username query-str 0))
  ([^String username ^String query-str ^Integer offset]
   (let [query-str (-> (or query-str "")
                       (s/replace #"[,.;:]" " ")            ; Consider commas whitespace
                       (s/replace #"[$!&=\-\*|%&^]" "")     ; Remove characters which could cause it to barf
                       s/trim
                       (s/replace #"\s+" "|")               ; Replace white space sequences with a single or operator
                       )
         params    {:limit    result-limit
                    :offset   offset
                    :username username
                    ;; Query won't be used in the case of get-thoughts, but bind it on let
                    ;; since we'll need it twice on search.
                    :query    query-str}
         result    (if (empty? query-str)
                     {:total   (-> (db/get-thought-count params) first :count)
                      :results (db/get-thoughts params)}
                     {:total   (-> (db/search-thought-count params) first :count)
                      :results (db/search-thoughts params)})
         ]
     (assoc result :pages (int (Math/ceil (/ (:total result) result-limit))))
     )))

(defn query-memory-thread
  "Returns a list with all the memories belonging to a root id"
  [id]
  (db/get-thread-by-root-id {:id id}))

