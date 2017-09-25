(ns memento.test.routes.api.auth
  (:require [clojure.test :refer :all]
            [clj-time.core :as t]
            [memento.handler :refer [app]]
            [memento.config :refer [env]]
            [memento.db.user :as user]
            [memento.test.db.core :as tdb]
            [memento.test.routes.helpers :refer [post-request patch-request get-request put-request del-request invoke-login]]
            [memento.db.core :refer [*db*]]
            [numergent.auth :refer [create-auth-token decode-token]] ; Only for validation, all other calls should go through the API
            [mount.core :as mount]))

(use-fixtures
  :once
  (fn [f]
    (mount/start
      #'memento.config/env
      #'memento.db.core/*db*)
    (f)))

;;;;
;;;; Tests
;;;;


;;;
;;; Authentication
;;;

(deftest test-login
  (tdb/wipe-database! *db*)
  (user/create! "user1" "password1")
  (testing "We get a login token when authenticating with a valid username/password"
    (let [[response data] (post-request "/api/auth/login" {:username "user1" :password "password1"} nil)]
      (is (= 200 (:status response)))
      (is (string? data))
      (decode-token (:auth-conf env) data)))
  (testing "Auth is not case-sensitive on the username"
    (let [[response data] (post-request "/api/auth/login" {:username "User1" :password "password1"} nil)]
      (is (= 200 (:status response)))
      (is (string? data))
      (decode-token (:auth-conf env) data)))
  (testing "We get a 401 when authenticating with an invalid username/password"
    (let [[response data] (post-request "/api/auth/login" {:username "user2" :password "password1"} nil)]
      (is (= 401 (:status response)))
      (is (= "Authentication error" data))))
  (testing "Auth is case-sensitive on the password"
    (let [[response data] (post-request "/api/auth/login" {:username "user1" :password "Password1"} nil)]
      (is (= 401 (:status response)))
      (is (= "Authentication error" data))))
  )

(deftest test-auth-validate
  (tdb/wipe-database! *db*)
  (user/create! "user1" "password1")
  (testing "We can validate a token we just created through login"
    (let [[_ token] (post-request "/api/auth/login" {:username "user1" :password "password1"} nil)
          [response body] (get-request "/api/auth/validate" nil token)]
      (is (some? token))
      (is (= 200 (:status response)))
      (is (= token body))))
  (testing "We  can validate a token we created directly"
    (let [token (create-auth-token (:auth-conf env) "user1")
          [response body] (get-request "/api/auth/validate" nil token)]
      (is (some? token))
      (is (= 200 (:status response)))
      (is (decode-token (:auth-conf env) body))
      (is (= token body))))
  (testing "We cannot validate an expired token"
    (let [token (create-auth-token (:auth-conf env) "user1" (t/minus (t/now) (t/minutes 1)))
          [response _] (get-request "/api/auth/validate" nil token)]
      (is (some? token))
      (is (= 401 (:status response)))))
  (testing "We cannot validate a nil token"
    (let [[response _] (get-request "/api/auth/validate" nil nil)]
      (is (= 401 (:status response)))))
  (testing "We cannot validate gibberish"
    (let [[response _] (get-request "/api/auth/validate" nil "I'MFORREAL")]
      (is (= 401 (:status response)))))
  )


(deftest test-signup
  (tdb/wipe-database! *db*)
  (let [username "newuser"
        password "password"]
    (testing "Attempting to log in with the credentials initially results on a 401"
      (let [[response data] (post-request "/api/auth/login" {:username username :password password} nil)]
        (is (= 401 (:status response)))
        (is (= "Authentication error" data))))
    (testing "We get a login token when signing up with a valid username/password"
      (let [[response data] (post-request "/api/auth/signup" {:username username :password password} nil)]
        (is (= 201 (:status response)))
        (is data)
        (is (decode-token (:auth-conf env) data))
        ))
    (testing "Attempting to log in with the credentials after creating it results on a token"
      (let [[response data] (post-request "/api/auth/login" {:username username :password password} nil)]
        (is (= 200 (:status response)))
        (is (decode-token (:auth-conf env) data))))
    (testing "Attempting to sign up with the same username/password results on an error"
      (let [[response data] (post-request "/api/auth/signup" {:username username :password password} nil)]
        (is (= 409 (:status response)))
        (is (= "Invalid username/password combination" data))))
    (testing "Attempting to sign up with the same username results on an error"
      (let [[response data] (post-request "/api/auth/signup" {:username username :password "password2"} nil)]
        (is (= 409 (:status response)))
        (is (= "Invalid username/password combination" data))))
    (testing "Attempting to sign up with empty username fails"
      (let [[response data] (post-request "/api/auth/signup" {:username "" :password password} nil)]
        (is (= 409 (:status response)))
        (is (= "Invalid username/password combination" data))))
    (testing "Attempting to sign up with empty password fails"
      (let [[response data] (post-request "/api/auth/signup" {:username username :password ""} nil)]
        (is (= 409 (:status response)))
        (is (= "Invalid username/password combination" data))))
    (testing "We get a login token when signing up with a new username/password"
      (let [[response data] (post-request "/api/auth/signup" {:username "u1" :password "p1"} nil)]
        (is (= 201 (:status response)))
        (is (decode-token (:auth-conf env) data))))
    ))
