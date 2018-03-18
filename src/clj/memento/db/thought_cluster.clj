(ns memento.db.thought-cluster
  (:require [clojure.java.jdbc :as jdbc]
            [memento.config :refer [env]]
            [memento.db.core :refer [*db*] :as db]
            [memento.db.memory :as thought]
            [memento.misc.html :refer [remove-html clean-memory-text]]
            [schema.core :as s])
  (:import (java.util UUID)))


(defn add-thoughts
  "Adds a list of thoughts to a cluster."
  ([cluster-id thought-ids]
   (add-thoughts *db* cluster-id thought-ids))
  ([db cluster-id thought-ids]
   (s/validate [UUID] thought-ids)
   (if (not-empty thought-ids)
     (db/add-thoughts-to-cluster! db {:pairs (map #(vector cluster-id %) thought-ids)})
     0)))


(defn get-clusters
  "Gets clusters belonging to the specified username, or nil."
  [username]
  (not-empty (db/get-clusters {:username username})))


(defn get-thoughts
  "Gets the thoughts for a cluster id if the cluster belongs to the specified username.
  Otherwise it returns nil.

  Will limit the thoughts returned at 1000. Does not do pagination,
  current page will always be 0."
  [username cluster-id]
  (assoc (thought/query username nil 0 true :limit 1000
                        :extra-joins (db/join-cluster-thoughts {:id cluster-id :username username}))
    :current-page 0))


(defn cluster-thoughts
  "Cluster a list of thought-ids for a username. Validates that all thought ids belong
  to the user and will exclude those that do not. Will not create empty clusters.

  Returns the cluster item or nil."
  [username thought-ids]
  (jdbc/with-db-transaction
    [trans-conn *db*]
    (let [cluster  (db/create-cluster! trans-conn {:username username})
          filtered (db/filter-thoughts-owner {:username    username
                                              :thought-ids thought-ids})
          added    (add-thoughts trans-conn (:id cluster) (map :id filtered))]
      (if (pos? added)
        cluster
        (do
          (jdbc/db-set-rollback-only! trans-conn)
          nil)))))

(defn remove-thought
  "Deletes a thought from a user's cluster"
  [username cluster-id thought-id]
  (jdbc/with-db-transaction
    [trans-conn *db*]
    (let [result (db/remove-thought-from-cluster! trans-conn {:username   username
                                                              :cluster-id cluster-id
                                                              :thought-id thought-id})]
      (db/delete-cluster-if-empty! trans-conn {:id cluster-id})
      result)))