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
  "Retrieves all pending reminders. These are the ones where the :next-date
  is before the current time. This means that we need the :next-date to
  be set to nil when a reminder's last remind date has passed."
  ([username]
   (get-pending username (tc/to-sql-date (tm/now))))
  ([username min-date]
   (jdbc/with-db-transaction
     [trans-conn *db*]
     (db/get-pending-reminders trans-conn {:username username :min-date min-date}))))

(defn add-days-to-now
  "Adds a number of days to the current date. If it receives nil, it returns nil."
  [days]
  (when (some? days)
    (tc/to-sql-date (tm/plus (tm/now)
                             (tm/days days)))))

(defn add-random-days
  "Adds a number of random days to a date"
  [r date]
  (when (some? date)
    ;; Use tm/minutes because it gives us more granularity, since instant functions
    ;; expect integers
    (tc/to-sql-date (tm/plus (tc/to-date-time date)
                             (tm/minutes (* 24 60 (rand r)))))))

(defn- add-schedule-to-record
  "Adds a new schedule to reminder record based on its type. Expects a connection to use."
  [conn reminder]
  (let [reminder-type (db/get-reminder-type conn {:id (:type-id reminder)})
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
                     :next-date  (add-random-days 1 (add-days-to-now (first schedule)))})))

(defn create!
  "Saves a new reminder associated with a memory."
  [reminder]
  (jdbc/with-db-transaction
    [trans-conn *db*]
    (db/create-reminder! trans-conn (add-schedule-to-record trans-conn reminder))))


(defn update-reminder-date!
  "Updates a reminder's next date and its properties"
  [id next-date properties]
  (jdbc/with-db-transaction
    [trans-conn *db*]
    (db/update-reminder-date! trans-conn {:id         id
                                          :next-date  next-date
                                          :properties properties})))

(defn mark-as-viewed!
  "Sets a reminder as viewed.

  For `once`, it will just mark the next date as nil.

  For `spaced` repetition, it'll either get the next date in line or mark it
  as nil. It will not move the day-idx past the maximum day count.
  "
  [id]
  (jdbc/with-db-transaction
    [trans-conn *db*]
    ;; This is crying out for a protocol or something
    (let [item       (db/get-reminder trans-conn {:id id})
          properties (:properties item)
          day-count  (count (get-in item [:properties :days]))
          day-idx    (inc (min (dec day-count)              ; Ensure it never goes past the index
                               (or (get-in item [:properties :day-idx])
                                   0)))
          new-state  (condp = (:type-id item)
                       ;; One-off reminders. Not testing it yet, may end up discarding it.
                       "once" (assoc item :properties nil :next-date nil)
                       ;; Spaced repetition
                       "spaced" (cond
                                  ;; If it already contains the days, then move it along
                                  (contains? properties :days)
                                  (-> item
                                      (assoc :next-date
                                             (->> (get-in item [:properties :days])
                                                  ;; I drop N elements instead of doing nth because
                                                  ;; this saves me from having to do exception handling
                                                  ;; for the last element
                                                  (drop day-idx)
                                                  first
                                                  add-days-to-now
                                                  (add-random-days day-idx)))
                                      (assoc-in [:properties :day-idx] day-idx))
                                  ;; If we find a legacy case which does not include the days,
                                  ;; add a new schedule to the default.
                                  ;; TODO REMOVE once we no longer have legacy reminders.
                                  ;; See issue #73.
                                  :else (add-schedule-to-record trans-conn item))
                       )]
      (db/update-reminder-date! trans-conn (select-keys new-state [:id :next-date :properties])))))


