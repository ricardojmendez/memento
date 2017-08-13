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

(defn create!
  "Saves a new reminder associated with a memory."
  [reminder]
  (jdbc/with-db-transaction
    [trans-conn *db*]
    (let [reminder-type (db/get-reminder-type {:id (:type_id reminder)})
          ;; TODO: Support other types, for now this will only expand to
          ;; a day schedule
          schedule      (->> (get-in reminder-type [:schedule :days])
                             (map #(tc/to-sql-date (tm/plus (tm/now) (tm/days %))))
                             sort                           ; (sort nil) -> empty, so it needs to come first
                             not-empty)
          ;; To support one-off reminders, we need to get the
          ;; next-date when we get called
          reminder      (merge {:properties {:schedule schedule}
                                :next_date  (first schedule)}
                               reminder)]
      (db/create-reminder! trans-conn reminder))))

(defn mark-as-viewed!
  "Sets a reminder as viewed.

  For `once`, it will just mark the next date as nil.

  For `spaced` repetition, it'll either get the next date in line or mark it
  as nil."
  [id]
  (jdbc/with-db-transaction
    [trans-conn *db*]
    ;; This is crying out for a protocol or something
    (let [item      (db/get-reminder trans-conn {:id id})
          next-date (condp = (:type_id item)
                      "once" nil
                      "spaced" (->> (get-in item [:properties :schedule])
                                    ;; Turns out these come out as strings. Need to coerce them.
                                    (map tc/to-date-time)
                                    (filter #(tm/after? % (tm/now)))
                                    sort
                                    first
                                    tc/to-date))]
      (db/update-reminder-date! trans-conn {:id id :next_date next-date}))))


