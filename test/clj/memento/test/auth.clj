(ns memento.test.auth
  (:require [clojure.test :refer :all]
            [clj-time.core :as t]
            [luminus-migrations.core :as migrations]
            [memento.config :refer [env]]
            [memento.test.db.core :as tdb]
            [memento.db.user :as user]
            [mount.core :as mount]
            [memento.db.core :refer [*db*] :as db]
            [numergent.auth :as auth]
            [clojure.java.io :as io])
  (:import (org.bouncycastle.jcajce.provider.asymmetric.rsa BCRSAPrivateCrtKey)))


(use-fixtures
  :once
  (fn [f]
    (mount/start
      #'memento.config/env
      #'memento.db.core/*db*)
    (f)))


(def pkey #'auth/pkey)

(deftest test-pkey
  (let [auth-conf       (:auth-conf env)
        key-string      (slurp (io/resource (:privkey auth-conf)))
        key-from-file   (pkey auth-conf)
        key-from-string (pkey (assoc auth-conf :privkey key-string))]
    (is key-from-file)
    (is (instance? BCRSAPrivateCrtKey key-from-file))
    (is (= key-from-file key-from-string))))


(deftest test-create-auth-token
  (tdb/wipe-database! *db*)
  (user/create! "user1" "password1")
  (let [token (auth/create-auth-token (:auth-conf env) "user1")]
    (is token)
    (is (< 0 (count token))))
  (let [expiry (t/plus (t/now) (t/minutes 1))
        token (auth/create-auth-token (:auth-conf env) "user1" expiry)]
    (is token)))


(deftest test-decode-auth-token
  (tdb/wipe-database! *db*)
  (user/create! "user1" "password1")
  (testing "Attempt to decode a good token"
    (let [token  (auth/create-auth-token (:auth-conf env) "user1")
          result (auth/decode-token (:auth-conf env) token)]
      (is token)
      (is result)
      (is (= "user1" (:username result)))
      (is (pos? (:exp result)))
      ))
  (testing "Attempt to decode a token created with a specific validity"
    (let [expiry (t/plus (t/now) (t/minutes 2))
          token  (auth/create-auth-token (:auth-conf env) "user1" expiry)
          result (auth/decode-token (:auth-conf env) token)]
      (is result)
      (is (= "user1" (:username result)))))
  (testing "Attempt to decode an already expired token should fail"
    (let [token  (auth/create-auth-token (:auth-conf env) "user1" (t/minus (t/now) (t/seconds 1)))
          result (auth/decode-token (:auth-conf env) token)]
      (is (nil? result))))
  (testing "Attempt to decode an invalid token"
    (is (nil? (auth/decode-token (:auth-conf env) "invalid")))))

(deftest test-decode-for-buddy
  (testing "Attempt to decode a good token"
    (let [token  (auth/create-auth-token (:auth-conf env) "user1")
          result (auth/decode-for-buddy (:auth-conf env) nil token)]
      (is result)
      (is (= "user1" (:username result)))
      (is (= token (:token result)))
      (is (some? (:exp result)))))
  (testing "Decode function does not care about the first parameter"
    (let [token  (auth/create-auth-token (:auth-conf env) "user1")
          result (auth/decode-for-buddy (:auth-conf env) "gibberish" token)]
      (is result)
      (is (= "user1" (:username result)))
      (is (= token (:token result)))
      (is (some? (:exp result)))))
  (testing "Attempt to decode a bad token"
    (let [result (auth/decode-for-buddy (:auth-conf env) nil "gibberish")]
      (is (nil? result))))
  (testing "Attempt to decode an already expired token should fail"
    (let [token  (auth/create-auth-token (:auth-conf env) "user1" (t/minus (t/now) (t/seconds 1)))
          result (auth/decode-for-buddy (:auth-conf env) nil token)]
      (is (nil? result)))))


(deftest test-username-lower-case
  (tdb/wipe-database! *db*)
  (user/create! "User1" "password1")
  ;; Confirm we always get the username in lower case for the token
  (let [token  (auth/create-auth-token (:auth-conf env) "User1")
        result (auth/decode-token (:auth-conf env) token)]
    (is token)
    (is (< 0 (count token)))
    (is (= "user1" (:username result)))))

