(ns memento.test.routes.api.reminder
  (:require [clojure.test :refer :all]
            [clj-time.coerce :as c]
            [clj-time.core :as t]
            [cognitect.transit :as transit]
            [memento.handler :refer [app]]
            [memento.db.user :as user]
            [memento.test.db.core :as tdb]
            [memento.test.db.memory :as tdm]
            [memento.test.db.user :as tdu]
            [memento.test.routes.helpers :refer [post-request get-request put-request del-request invoke-login]]
            [memento.db.core :refer [*db*] :as db]
            [ring.mock.request :refer [request header body]]
            [clojure.string :as string]
            [numergent.auth :as auth]))

;;;;
;;;; Tests
;;;;


(deftest test-add-reminder
  (tdu/init-placeholder-data!)
  (user/create! "user1" "password1")
  (let [token (invoke-login {:username "user1" :password "password1"})
        [_ record] (post-request "/api/thoughts" {:thought "Just a thought"} token)]
    ;; Verify the basics
    (is (string? token))
    (is (map? record))
    ;; On to the tests
    (testing "Attempting to add a reminder without a token results in a 400"
      (let [[response _] (post-request "/api/reminder" {:thought-id (:id record) :type-id "spaced"} nil)]
        (is (= 400 (:status response)))))
    (testing "We can add a new reminder to a thought"
      (let [[response record] (post-request "/api/reminders" {:thought-id (:id record) :type-id "spaced"} token)]
        ;; TODO: Expand tests, just verifying the basics work right now, since the API may change
        (is (= 201 (:status response)))
        (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
        (is (map? record))
        (is (:id record))
        (is (= "spaced" (:type_id record)))
        (is (= 4 (count (get-in record [:properties :schedule]))))
        (is (= (str "http://localhost/api/reminders/" (:id record)) (get-in response [:headers "Location"])))
        ))
    (testing "After adding a reminder, we can retrieve it"
      (let [[_ item] (post-request "/api/reminders" {:thought-id (:id record) :type-id "spaced"} token)
            [response item-2] (get-request (str "/api/reminders/" (:id item)) nil token)]
        (is (= 200 (:status response)))
        (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
        (is (= item item-2))))
    (testing "Trying to retrieve a reminder from someone other than the owner fails"
      (let [[_ item] (post-request "/api/reminders" {:thought-id (:id record) :type-id "spaced"} token)
            invalid-token (invoke-login {:username tdu/ph-username :password tdu/ph-password})
            [response item-2] (get-request (str "/api/reminders/" (:id item)) nil invalid-token)]
        (is (= 404 (:status response)))
        (is (some? item))
        (is (nil? item-2))))
    )
  )

;; TODO: Tests for
;; - Get active reminders
;; - Mark a reminder as expired
