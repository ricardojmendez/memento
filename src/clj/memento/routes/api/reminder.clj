(ns memento.routes.api.reminder
  (:require [numergent.auth :as auth]
            [memento.db.user :as user]
            [memento.db.memory :as memory]
            [memento.db.reminder :as reminder]
            [numergent.utils :as utils]
            [ring.util.http-response :refer [ok unauthorized conflict created
                                             bad-request! not-found forbidden
                                             no-content]])
  (:import (java.util UUID)))


(defn create-new
  "Saves a new reminder"
  [username thought-id type-id]
  (if (memory/get-if-owner username thought-id)
    (let [item (reminder/create! {:thought_id thought-id :type_id type-id})]
      (created (str "/api/reminders/" (:id item))
               (assoc item :username username)))
    (not-found)))

(defn get-reminder
  "Gets a reminder by id"
  [username id]
  (if-let [existing (reminder/get-if-owner username id)]
    (ok existing)
    (not-found)))

(defn set-next-date
  "Sets the next date for a reminder"
  [username id next-date]
  (if-let [existing (reminder/get-if-owner username id)]
    (do
      (reminder/update-reminder-date! id next-date (:properties existing))
      (no-content))
    (not-found)))

(defn get-pending-reminders
  "Retrieves all pending reminders for a user"
  [username]
  (ok (reminder/get-pending username)))

(defn mark-as-viewed!
  "Marks a reminder as viewed. Returns the number of records updated"
  [username id]
  (if (reminder/get-if-owner username id)
    (ok (reminder/mark-as-viewed! id))
    (not-found)))