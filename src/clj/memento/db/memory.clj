(ns memento.db.memory
  (:require [environ.core :refer [env]]
            [clj-time.format :as tf]
            [clj-time.coerce :as tc]
            [memento.db.core :as db])
  (:import (java.util Date)))

(defn now [] (Date.))

(defn- spit-memory! [item]
  (spit "memento.out" item :append true)
  (spit "memento.out" "\n" :append true))

(defn format-created
  "Receives a collection of memories and formats the create date to a string"
  [memories]
  (map #(assoc % :created (tf/unparse (tf/formatters :date-hour-minute) (tc/from-date (:created %)))) memories))

(defn save-memory!
  "Trivial save. For now everything will go to one user."
  [memory]
  (let [item (merge {:created (now) :username "ricardo"} memory)]
    (spit-memory! item)
    (db/create-thought! item)))

(defn query-memories
  "Trivial query - return everything from one user"
  ([]
   (query-memories nil))
  ([^String query-str]
   (let [params {:limit 25
                 :offset 0
                 :username "ricardo"}]
     (if (nil? query-str)
       (db/get-thoughts params)
       (db/search-thoughts (assoc params :query (clojure.string/replace query-str " " "|"))))
     )))

