(ns memento.db.reminder
  (:require [clj-time.format :as tf]
            [clj-time.coerce :as tc]
            [clj-time.core :as tm]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as s]
            [memento.config :refer [env]]
            [memento.db.core :refer [*db*] :as db]
            ))

;; TODO
;; - query
;; - mark as done

(defn get-by-id
  [id]
  (jdbc/with-db-transaction
    [trans-conn *db*]
    (db/get-reminder trans-conn {:id id})))

(defn create!
  "Saves a new reminder associated with a memory."
  [reminder]
  (jdbc/with-db-transaction
    [trans-conn *db*]
    (let [reminder-type (db/get-reminder-type {:id (:type_id reminder)})
          ;; TODO: Support other types, for now this will only expand to
          ;; a day schedule”
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

