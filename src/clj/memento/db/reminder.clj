(ns memento.db.reminder
  (:require [memento.config :refer [env]]
            [clj-time.format :as tf]
            [clj-time.coerce :as tc]
            [clj-time.core :as tm]
            [clojure.string :as s]
            [memento.db.core :refer [*db*] :as db]
            [clojure.java.jdbc :as jdbc]
            [clj-time.coerce :as c]
            [clj-time.core :as t]))

;; TODO
;; - create
;; - query
;; - mark as done
;; -

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

