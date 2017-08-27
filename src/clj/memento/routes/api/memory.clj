(ns memento.routes.api.memory
  (:require [memento.db.user :as user]
            [memento.db.memory :as memory]
            [numergent.auth :as auth]
            [numergent.utils :as utils]
            [ring.util.http-response :refer [ok unauthorized conflict created
                                             bad-request! not-found forbidden
                                             no-content]]
            [clojure.string :as string])
  (:import (java.util UUID)))


(defn query-thoughts
  "Gets the next set of memories for a username, potentially with a search query"
  [username query page]
  (let [offset (* page memory/result-limit)]
    (ok (-> (memory/query username query offset)
            (assoc :current-page page)))))

(defn save-thought
  "Saves a new thought"
  [username thought refine-id]
  (let [trimmed (string/trim thought)]
    (if (not-empty trimmed)
      (let [record (memory/create! {:username  username
                                    :thought   trimmed
                                    :refine_id refine-id})]
        (created (str "/api/thoughts/" (:id record))
                 record))
      (bad-request! "Cannot add empty thoughts"))))

(defn update-thought
  "Updates an existing thought"
  [username id thought]
  (let [trimmed  (string/trim thought)
        existing (memory/get-if-owner username id)]
    (cond
      (not existing) (not-found)
      (= :closed (:status existing)) (forbidden "Cannot update closed thoughts")
      :else (ok (memory/update! {:id id :thought trimmed}))
      )))

(defn delete-thought
  "Deletes an existing thought"
  [username id]
  (let [existing (memory/get-if-owner username id)]
    (cond
      (not existing) (not-found)
      (= :closed (:status existing)) (forbidden "Cannot update closed thoughts")
      :else (do
              (memory/delete! id)
              (no-content)))))

(defn get-thread
  "Returns a thread by id"
  [username id]
  (ok (->> id
           memory/query-thread
           (filter #(= (:username %) username))
           (hash-map :id id :results))))