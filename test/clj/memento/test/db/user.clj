(ns memento.test.db.user
  (:require [clojure.test :refer :all]
            [memento.db.core :refer [*db*] :as db]
            [memento.db.user :as user]
            [memento.test.db.core :as tdb]
            [buddy.hashers :as hashers]))


;;;;
;;;; Helper functions
;;;;

(def ph-username "mocky")
(def ph-password "supersecret")



(defn init-placeholder-data!
  "Wipes the database and inserts a test user. We don't care about adding
  a hashed password since it's there only for foreign key purposes."
  []
  (tdb/wipe-database! *db*)
  (user/create! ph-username ph-password))


(deftest test-create-user
  (tdb/wipe-database! *db*)
  (testing "We can create a new user successfully"
    (let [result (user/create! "user1" "password1")]
      (is (:success? result))
      (is (= "user1" (:username result)))
      (is (hashers/check "password1" (:password result)))
      ))
  (testing "Attempting to create a user twice returns an error"
    (let [result (user/create! "user1" "sameuser")]
      (is (false? (:success? result)))
      (is (nil? (:username result)))
      (is (nil? (:password result)))
      (is (.contains (:message result) "duplicate key"))))
  (testing "Attempting to create a user twice returns an error even with different case"
    (let [result (user/create! "USER1" "sameuser")]
      (is (false? (:success? result)))
      (is (nil? (:username result)))
      (is (nil? (:password result)))
      (is (.contains (:message result) "duplicate key"))))
  (testing "Sending an empty password returns an error"
    (let [result (user/create! "user1" nil)]
      (is (false? (:success? result)))
      (is (nil? (:username result)))
      (is (nil? (:password result)))
      (is (.contains (:message result) "password is required")))
    (let [result (user/create! "user1" "")]
      (is (false? (:success? result)))
      (is (nil? (:username result)))
      (is (nil? (:password result)))
      (is (.contains (:message result) "password is required"))))
  (testing "Sending an empty or nil username returns an error"
    (let [result (user/create! nil "password1")]
      (is (false? (:success? result)))
      (is (.contains (:message result) "username is required")))
    (let [result (user/create! "" "password1")]
      (is (false? (:success? result)))
      (is (.contains (:message result) "username is required"))))
  (testing "User login is converted to lowercase before creation"
    (let [result  (user/create! "USER2" "password1")
          get-res (db/get-user *db* {:username "user2"})]
      (is (:success? result))
      (is (= "user2" (:username result)))
      (is (= "user2" (:username get-res)))
      (is (hashers/check "password1" (:password result)))
      )))


(deftest test-validate-user
  (tdb/wipe-database! *db*)
  (user/create! "user1" "password1")
  (user/create! "user2" "password2")
  (is (user/validate "user1" "password1"))
  (is (user/validate "user2" "password2"))
  ;; Validation is not case sensitive
  (is (user/validate "User1" "password1"))
  (is (not (user/validate "user1" "password2")))
  (is (not (user/validate "user1" "")))
  (is (not (user/validate "user1" nil)))
  (is (not (user/validate "" "password1")))
  (is (not (user/validate nil "password1")))
  (is (not (user/validate nil nil))))