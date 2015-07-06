(ns memento.test.db.core
  (:require [clojure.test :refer :all]
            [memento.db.core :as db]
            [yesql.core :refer [defqueries]])
  (:import java.sql.BatchUpdateException))


;;;;
;;;; Definitions
;;;;


(defqueries "sql/test-queries.sql" {:connection db/db-spec})


;;;;
;;;; Basic tests
;;;;


(deftest test-create-user
  (wipe-database!)
  (let [result (db/create-user! {:username "testuser" :password "unencrypted"})]
    (is (= 1 result))))


(deftest test-get-user
  (wipe-database!)
  ;; We get an empty list when querying for an invalid login
  (let [result (db/get-user {:username "nothere"})]
    (is (empty? result)))
  ;; create-user! only saves the information to the db, so the password goes in unhashed
  (let [record {:username "testuser" :password "unencrypted"}
        _      (db/create-user! record)
        result (db/get-user {:username "testuser"})]
    (is (= 1 (count result)))
    (is (= record (first result)))))


(deftest create-user-twice-fails
  (wipe-database!)
  (db/create-user! {:username "testuser" :password "nothere"})
  (is (thrown? BatchUpdateException (db/create-user! {:username "testuser" :password "notagain"}))))
