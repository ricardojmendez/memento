(ns memento.routes.api.reminder
  (:require [memento.db.memory :as memory]
            [memento.db.reminder :as reminder]
            [ring.util.http-response :refer [ok unauthorized conflict created
                                             bad-request! not-found forbidden
                                             no-content]]))


(defn create!
  "Saves a new reminder for a thought. Returns not-found if it can't find a thought-id
  belonging to the user."
  [username thought-id type-id]
  (if (memory/get-if-owner username thought-id)
    (let [item (reminder/create! {:thought-id thought-id :type-id type-id})]
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