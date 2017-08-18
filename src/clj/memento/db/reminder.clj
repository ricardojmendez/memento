(ns memento.db.reminder
  (:require [clj-time.format :as tf]
            [clj-time.coerce :as tc]
            [clj-time.core :as tm]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as s]
            [memento.config :refer [env]]
            [memento.db.core :refer [*db*] :as db]
            ))

(defn get-by-id
  [id]
  (jdbc/with-db-transaction
    [trans-conn *db*]
    (db/get-reminder trans-conn {:id id})))

(defn get-if-owner
  [username id]
  (let [item (get-by-id id)]
    (when (= username (:username item))
      item)))

(defn get-pending
  "Retrieves all pending reminders. These are the ones where the :next_date
  is before the current time. This means that we need the :next_date to
  be set to nil when a reminder's last remind date has passed."
  ([username]
   (get-pending username (tc/to-sql-date (tm/now))))
  ([username min-date]
   (jdbc/with-db-transaction
     [trans-conn *db*]
     (db/get-pending-reminders trans-conn {:username username :min_date min-date}))))

(defn add-days-to-now
  "Adds a number of days to the current date. If it receives nil, it returns nil."
  [days]
  (when (some? days)
    (tc/to-sql-date (tm/plus (tm/now) (tm/days days)))))

(defn- add-schedule-to-record
  "Adds a new schedule to reminder record based on its type. Expects a connection to use."
  [conn reminder]
  (let [reminder-type (db/get-reminder-type conn {:id (:type_id reminder)})
        ;; TODO: Support other types, for now this will only expand to
        ;; a day schedule
        schedule      (->> (get-in reminder-type [:schedule :days])
                           sort                             ; (sort nil) -> empty, so it needs to come first
                           not-empty)]
    ;; This doesn't support one-off reminders, since the date will always be overwritten
    ;; by the schedule.
    ;;
    ;; To support "once" reminders, we should:
    ;; - Not save the properties
    ;; - Not overwrite the next_date
    (merge reminder {:properties {:days    schedule
                                  :day-idx 0}
                     :next_date  (add-days-to-now (first schedule))})))

(defn create!
  "Saves a new reminder associated with a memory."
  [reminder]
  (jdbc/with-db-transaction
    [trans-conn *db*]
    (db/create-reminder! trans-conn (add-schedule-to-record trans-conn reminder))))


(defn mark-as-viewed!
  "Sets a reminder as viewed.

  For `once`, it will just mark the next date as nil.

  For `spaced` repetition, it'll either get the next date in line or mark it
  as nil."
  [id]
  (jdbc/with-db-transaction
    [trans-conn *db*]
    ;; This is crying out for a protocol or something
    (let [item       (db/get-reminder trans-conn {:id id})
          properties (:properties item)
          day-idx    (inc (or (get-in item [:properties :day-idx]) 0))
          new-state  (condp = (:type_id item)
                       "once" (assoc item :properties nil :next_date nil)
                       "spaced" (cond
                                  ;; If it already contains the days, then move it along
                                  (contains? properties :days)
                                  (-> item
                                      (assoc :next_date
                                             (->> (get-in item [:properties :days])
                                                  ;; I drop N elements instead of doing nth because
                                                  ;; this saves me from having to do exception handling
                                                  ;; for the last element
                                                  (drop day-idx)
                                                  first
                                                  add-days-to-now))
                                      (assoc-in [:properties :day-idx] day-idx))
                                  ;; If we find a legacy case which does not include the days,
                                  ;; add a new schedule to the default.
                                  ;; TODO REMOVE once we no longer have legacy reminders.
                                  ;; See issue #73.
                                  :else (add-schedule-to-record trans-conn item))
                       )]
      (db/update-reminder-date! trans-conn (select-keys new-state [:id :next_date :properties])))))


