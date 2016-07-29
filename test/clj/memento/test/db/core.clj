(ns memento.test.db.core
  (:require [clojure.test :refer :all]
            [conman.core :as conman]
            [memento.db.core :refer [*db*] :as db]
            [memento.config :refer [env]]
            [mount.core :as mount]
            [luminus-migrations.core :as migrations])
  (:import (java.sql BatchUpdateException)))


;;;;
;;;; Definitions
;;;;

(use-fixtures
  :once
  (fn [f]
    (mount/start
      #'memento.config/env
      #'memento.db.core/*db*)
    (migrations/migrate ["migrate"] (select-keys env [:database-url]))
    (f)))

(conman/bind-connection *db* "sql/test-queries.sql")

;;;;
;;;; Basic tests
;;;;


(deftest test-create-user
  (wipe-database! *db*)
  (let [result (db/create-user!  *db* {:username "testuser" :password "unencrypted"})]
    (is (= 1 result))))


(deftest test-get-user
  (wipe-database! *db*)
  ;; We get an empty list when querying for an invalid login
  (let [result (db/get-user *db* {:username "nothere"})]
    (is (empty? result)))
  ;; create-user! only saves the information to the db, so the password goes in unhashed
  (let [record {:username "testuser" :password "unencrypted"}
        _      (db/create-user! *db* record)
        result (db/get-user *db* {:username "testuser"})]
    (is (= record result))))


(deftest create-user-twice-fails
  (wipe-database! *db*)
  (db/create-user! *db* {:username "testuser" :password "nothere"})
  (is (thrown? BatchUpdateException (db/create-user! *db* {:username "testuser" :password "notagain"}))))
