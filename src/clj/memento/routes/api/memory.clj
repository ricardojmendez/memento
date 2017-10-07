(ns memento.routes.api.memory
  (:require [memento.db.memory :as memory]
            [ring.util.http-response :refer [ok unauthorized conflict created
                                             bad-request! not-found forbidden
                                             no-content]]
            [clojure.string :as string]))


(defn query-thoughts
  "Gets the next set of memories for a username, potentially with a search query"
  [username query page include-archived?]
  (let [offset (* page memory/result-limit)]
    (ok (-> (memory/query username query offset include-archived?)
            (assoc :current-page page)))))

(defn save-thought
  "Saves a new thought"
  [username thought refine-id]
  (let [trimmed (string/trim thought)]
    (if (not-empty trimmed)
      (let [record (memory/create! {:username  username
                                    :thought   trimmed
                                    :follow-id refine-id})]
        (created (str "/api/thoughts/" (:id record))
                 record))
      (bad-request! "Cannot add empty thoughts"))))

(defn update-thought
  "Updates an existing thought"
  [username id thought]
  ;; TODO: I'm actually getting the memory twice: once here, and once when I
  ;; call memory/update!. I could just get it once, or re-use the DB
  ;; connection to avoid making multiple ones.
  (let [trimmed  (string/trim thought)
        existing (memory/get-if-owner username id)]
    (cond
      (not existing) (not-found)
      (= :closed (:status existing)) (forbidden "Cannot update closed thoughts")
      (empty? trimmed) (forbidden "Cannot blank a thought's text")
      :else (ok (memory/update! {:id id :thought trimmed}))
      )))

(defn archive-thought
  "Archived/de-archives a thought"
  [username id archived?]
  (let [existing (memory/get-if-owner username id)]
    (cond
      (not existing) (not-found)
      :else (ok (memory/archive! {:id id :archived? archived?})))))

(defn get-thought
  "Gets a thought by id"
  [username id]
  (if-let [existing (memory/get-if-owner username id)]
    (ok existing)
    (not-found)))

(defn delete-thought
  "Deletes an existing thought"
  [username id]
  (let [existing (memory/get-if-owner username id)]
    (cond
      (not existing) (not-found)
      (= :closed (:status existing)) (forbidden "Cannot delete closed thoughts")
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