(ns memento.routes.api.thought-cluster
  (:require [memento.db.thought-cluster :as cluster]
            [ring.util.http-response :refer [ok unauthorized conflict created
                                             bad-request! not-found forbidden
                                             no-content]]))

(defn get-list
  "Returns the list of clusters for a user.

  Will always return ok even if the list is empty"
  [username]
  (ok (not-empty (cluster/get-clusters username))))

(defn get-cluster
  "Retrieves the thoughts for an existing cluster.

  Not sending the cluster information because there's nothing there other
  than the creation date."
  [username id]
  (if-let [result (cluster/get-thoughts username id)]
    (ok result)
    (not-found)))

(defn create-cluster
  "Saves a new thought cluster"
  [username thought-ids]
  (if-let [record (cluster/cluster-thoughts username thought-ids)]
    (created (str "/api/clusters/" (:id record))
             record)
    (bad-request! "Cannot create an empty cluster")))

(defn remove-thought
  "Removes a thought from a cluster"
  [username cluster-id thought-id]
  (if (= 1 (cluster/remove-thought username cluster-id thought-id))
    (no-content)
    (not-found)))