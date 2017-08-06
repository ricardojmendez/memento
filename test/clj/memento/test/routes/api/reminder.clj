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
            [memento.auth :as auth]))

;;;;
;;;; Tests
;;;;

;; TODO: Add reminder tests

(deftest test-add-reminder
  (tdu/init-placeholder-data!)
  (user/create! "user1" "password1")
  (let [token (invoke-login {:username "user1" :password "password1"})
        [_ t1] (post-request "/api/thoughts" {:thought "Just a thought"} token)
        [_ t2] (post-request "/api/thoughts" {:thought "Another thought"} token)]
    (testing "Attempting to add a reminder without a token results in a 401"
      (testing "We can add a new memory"
        (let [[response _] (post-request "/api/reminders" {:for-id  (:id t1)
                                                           :type-id "spaced"} nil)]
          (clojure.pprint/pprint response)
          (is (= 401 (:status response)))
          )))
    (testing "We can add a new memory"
      (let [[response record] (post-request "/api/thoughts" {:thought "Just a thought"} token)]
        (is (= 201 (:status response)))
        (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
        (is (map? record))
        (is (:id record))
        (is (= "Just a thought" (:thought record)))
        (is (= (str "http://localhost/api/thoughts/" (:id record)) (get-in response [:headers "Location"])))
        ))
    (testing "After adding a memoy, we can query for it"
      (let [[_ {:keys [total results]}] (get-request "/api/thoughts" nil token)
            item (first results)]
        (is (seq? results))
        (is (= 1 (count results)))
        (is (= 1 total))
        (is (= "user1" (:username item)))
        (is (= "Just a thought" (:thought item)))
        (is (:created item))
        (is (:id item))))
    (testing "We can refine a memory through the API"
      (let [[_ {:keys [results]}] (get-request "/api/thoughts" nil token)
            m1  (first results)
            _   (post-request "/api/thoughts" {:thought "Refining an idea" :refine_id (:id m1)} token)
            [_ {:keys [results]}] (get-request "/api/thoughts" nil token)
            m2  (first results)
            [_ data] (get-request (str "/api/threads/" (:id m1)) nil token)
            ; m1 became a root after m2 was created, so we will expect it to have a root_id when returned
            m1r (assoc m1 :root_id (:id m1))
            ]
        (is m1)
        (is (nil? (:refine_id m1)))
        (is (nil? (:root_id m1)))
        (is (= (:id m1) (:refine_id m2)))
        (is (= (:id m1) (:root_id m2)))
        (is (= data {:results [m1r m2] :id (str (:id m1))}))
        ;; Test that we get an empty list if querying for a thread that does not belong to the user
        (let [new-token (invoke-login {:username tdu/ph-username :password tdu/ph-password})
              [_ data] (get-request (str "/api/threads/" (:id m1)) nil new-token)]
          (is new-token)
          (is (= {:results [] :id (str (:id m1))} data)))
        ))
    )
  (let [token (invoke-login {:username "User1" :password "password1"})]
    (testing "Username on memory addition is not case sensitive"
      (let [[response record] (post-request "/api/thoughts" {:thought "Just a new idea"} token)]
        (is (= 201 (:status response)))
        (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
        (is (map? record))
        (is (:id record))
        (is (= "Just a new idea" (:thought record)))
        )))
  )

