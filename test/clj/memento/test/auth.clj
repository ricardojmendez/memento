(ns memento.test.auth
  (:require [clojure.test :refer :all]
            [memento.auth :as auth]
            [memento.test.db.core :as tdb]
            [memento.db.user :as user]
            [memento.db.core :as db]))


(use-fixtures
  :once
  (fn [f]
    (db/connect!)
    (f)))

(deftest test-create-auth-token
  (db/run tdb/wipe-database!)
  (user/create-user! "user1" "password1")
  (let [token (auth/create-auth-token "user1" "password1")]
    (is token)
    (is (< 0 (count token))))
  (is (nil? (auth/create-auth-token "user1" "invalid")))
  )


(deftest test-decode-auth-token
  (db/run tdb/wipe-database!)
  (user/create-user! "user1" "password1")
  (testing "Attempt to decode a good token"
    (let [token  (auth/create-auth-token "user1" "password1")
          result (auth/decode-token token)]
      (is result)
      (is (= "user1" (:username result)))
      (is (< 0 (:exp result)))
      ))
  (testing "Attempt to decode an invalid token"
    (is (nil? (auth/decode-token "invalid"))))
  )


(deftest test-username-lower-case
  (db/run tdb/wipe-database!)
  (user/create-user! "User1" "password1")
  ;; Confirm we always get the username in lower case for the token
  (let [token (auth/create-auth-token "User1" "password1")
        result (auth/decode-token token)]
    (is token)
    (is (< 0 (count token)))
    (is (= "user1" (:username result))))
  )

