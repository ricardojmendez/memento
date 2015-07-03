(ns memento.test.db.user
  (:require [clojure.test :refer :all]
            [memento.db.user :as user]
            [memento.test.db.core :as tdb]
            [buddy.hashers :as hashers]))


(deftest test-create-user
  (tdb/wipe-database!)
  (testing "We can create a new user successfully"
    (let [result (user/create-user! "user1" "password1")]
      (is (:success? result))
      (is (= "user1" (:username result)))
      (is (hashers/check "password1" (:password result)))
      ))
  (testing "Attempting to create a user twice returns an error"
    (let [result (user/create-user! "user1" "sameuser")]
      (is (false? (:success? result)))
      (is (nil? (:username result)))
      (is (nil? (:password result)))
      (is (.contains (:message result) "duplicate key"))))
  (testing "Sending an empty password returns an error"
    (let [result (user/create-user! "user1" nil)]
      (is (false? (:success? result)))
      (is (nil? (:username result)))
      (is (nil? (:password result)))
      (is (.contains (:message result) "password is required")))
    (let [result (user/create-user! "user1" "")]
      (is (false? (:success? result)))
      (is (nil? (:username result)))
      (is (nil? (:password result)))
      (is (.contains (:message result) "password is required"))))
  (testing "Sending an empty or nil username returns an error"
    (let [result (user/create-user! nil "password1")]
      (is (false? (:success? result)))
      (is (.contains (:message result) "username is required")))
    (let [result (user/create-user! "" "password1")]
      (is (false? (:success? result)))
      (is (.contains (:message result) "username is required")))))


(deftest test-validate-user
  (tdb/wipe-database!)
  (user/create-user! "user1" "password1")
  (user/create-user! "user2" "password2")
  (is (user/validate-user "user1" "password1"))
  (is (user/validate-user "user2" "password2"))
  (is (not (user/validate-user "user1" "password2")))
  (is (not (user/validate-user "user1" "")))
  (is (not (user/validate-user "user1" nil)))
  (is (not (user/validate-user "" "password1")))
  (is (not (user/validate-user nil "password1")))
  (is (not (user/validate-user nil nil))))