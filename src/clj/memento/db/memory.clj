(ns memento.db.memory
  (:require [environ.core :refer [env]]
            [clj-time.format :as tf]
            [clj-time.coerce :as tc]
            [clojure.string :as s]
            [memento.db.core :as db])
  (:import (java.util Date)))

(defn now [] (Date.))
(def result-limit 10)

(defn- spit-memory! [item]
  (spit "memento.out" item :append true)
  (spit "memento.out" "\n" :append true))

(defn format-created
  "Receives a collection of memories and formats the create date to a string"
  [memories]
  (assoc memories :results
                  (map #(assoc % :created (tf/unparse (tf/formatters :date-hour-minute)
                                                      (tc/from-date (:created %))))
                       (:results memories))))

(defn save-memory!
  "Trivial save. For now everything will go to one user."
  [memory]
  (let [item (merge {:created (now)} memory)]
    (spit-memory! item)
    (db/create-thought! item)))

(defn query-memories
  "Queries for a user's memories"
  ([username]
   (query-memories username ""))
  ([^String username ^String query-str]
   (query-memories username query-str 0)
    )
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
         result    (if (empty? query-str)
                     {:total   (-> (db/get-thought-count params) first :count)
                      :results (db/get-thoughts params)}
                     {:total   (-> (db/search-thought-count params) first :count)
                      :results (db/search-thoughts params)})
         ]
     (assoc result :pages (int (Math/ceil (/ (:total result) result-limit))))
     )))

