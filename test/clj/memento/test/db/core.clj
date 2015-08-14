(ns memento.test.db.core
  (:require [clojure.test :refer :all]
            [memento.db.core :as db]
            [yesql.core :refer [defqueries]]
            [memento.db.migrations :as migrations])
  (:import (org.postgresql.util PSQLException)))


;;;;
;;;; Definitions
;;;;

(use-fixtures
  :once
  (fn [f]
    (db/connect!)
    #_ (migrations/migrate ["migrate"])
    (f)))

(defqueries "sql/test-queries.sql" @db/conn)


;;;;
;;;; Basic tests
;;;;


(deftest test-create-user
  (db/run wipe-database!)
  (let [result (db/run db/create-user! {:username "testuser" :password "unencrypted"})]
    (is (= 1 result))))


(deftest test-get-user
  (db/run wipe-database!)
  ;; We get an empty list when querying for an invalid login
  (let [result (db/run db/get-user {:username "nothere"})]
    (is (empty? result)))
  ;; create-user! only saves the information to the db, so the password goes in unhashed
  (let [record {:username "testuser" :password "unencrypted"}
        _      (db/run db/create-user! record)
        result (db/run db/get-user {:username "testuser"})]
    (is (= 1 (count result)))
    (is (= record (first result)))))


(deftest create-user-twice-fails
  (db/run wipe-database!)
  (db/run db/create-user! {:username "testuser" :password "nothere"})
  (is (thrown? PSQLException (db/run db/create-user! {:username "testuser" :password "notagain"}))))
