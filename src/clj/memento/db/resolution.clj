(ns memento.db.resolution
  (:require [memento.config :refer [env]]
            [memento.db.core :refer [*db*] :as db]
            [memento.misc.html :refer [remove-html-from-vals]]
            [clojure.java.jdbc :as jdbc])
  (:import (java.util Date UUID)))

(defn create!
  "Saves a raw resolution record to the database"
  [resolution]
  (jdbc/with-db-transaction
    [trans-conn *db*]
    (db/create-resolution! trans-conn resolution)))

(defn get-by-id
  "Loads a resolution by its id"
  [^UUID id]
  (db/get-resolution *db* {:id id}))

(defn get-if-owner
  "Returns a resolution if owned by a user, or nil otherwise"
  [username id]
  (let [existing (get-by-id id)]
    (when (= username (:username existing))
      existing)))


(defn get-list
  "Gets the list of resolutions for a username"
  [username include-archived?]
  (db/get-resolutions *db* {:username username
                            :all?     include-archived?}))


