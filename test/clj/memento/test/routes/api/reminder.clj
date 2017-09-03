(ns memento.test.routes.api.reminder
  (:require [clojure.test :refer :all]
            [clj-time.coerce :as c]
            [clj-time.core :as t]
            [memento.handler :refer [app]]
            [memento.db.user :as user]
            [memento.test.db.user :as tdu]
            [memento.test.db.core :as tdb]
            [memento.test.routes.helpers :refer [patch-request post-request get-request put-request del-request invoke-login]]
            [ring.mock.request :refer [request header body]]
            [mount.core :as mount]
            [memento.db.reminder :as reminder]))


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
        (is (= 201 (:status response)))
        (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
        (is (map? record))
        (is (:id record))
        (is (= "spaced" (:type_id record)))
        (is (= 4 (count (get-in record [:properties :days]))))
        (is (zero? (get-in record [:properties :day-idx])))
        (is (= (str "http://localhost/api/reminders/" (:id record)) (get-in response [:headers "Location"])))
        ))
    (testing "After adding a reminder, we can retrieve it"
      (let [[_ item] (post-request "/api/reminders" {:thought-id (:id record) :type-id "spaced"} token)
            [response item-2] (get-request (str "/api/reminders/" (:id item)) nil token)]
        (is (= 200 (:status response)))
        (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
        (is (= item item-2))))
    ;; Security concerns
    (testing "Trying to retrieve a reminder from someone other than the owner fails"
      (let [[_ item] (post-request "/api/reminders" {:thought-id (:id record) :type-id "spaced"} token)
            other-token (invoke-login {:username tdu/ph-username :password tdu/ph-password})
            [response item-2] (get-request (str "/api/reminders/" (:id item)) nil other-token)]
        (is (= 404 (:status response)))
        (is (some? item))
        (is (nil? item-2))))
    (testing "Trying to add a reminder to a thought by someone other than the owner fails"
      (let [all-before  (tdb/get-all-reminders)
            other-token (invoke-login {:username tdu/ph-username :password tdu/ph-password})
            all-after   (tdb/get-all-reminders)
            [response item] (post-request "/api/reminders" {:thought-id (:id record) :type-id "spaced"} other-token)]
        (is (= 404 (:status response)))
        (is (nil? item))
        ;; Ensure not only we 404'd, but there weren't any actual changes
        (is (= 3 (count all-before)))
        (is (= all-before all-after))))))

(deftest test-delete-thought
  (tdu/init-placeholder-data!)
  (user/create! "user1" "password1")
  (let [token (invoke-login {:username "user1" :password "password1"})
        [_ record] (post-request "/api/thoughts" {:thought "Just a thought"} token)]
    ;; On to the tests
    (testing "We can delete a thought with reminders"
      (let [[_ reminder] (post-request "/api/reminders" {:thought-id (:id record) :type-id "spaced"} token)
            [deleted _] (del-request "/api/thoughts" (:id record) token)
            ; Query post-delete
            [r-del-thought thoughts-after-delete] (get-request "/api/thoughts" nil token)
            [r-del-reminder reminder-after-delete] (get-request (str "/api/reminders/" (:id reminder)) nil token)]
        (is reminder)
        (is (= 204 (:status deleted)))
        (is (= 404 (:status r-del-reminder)))
        (is (zero? (:total thoughts-after-delete)))
        (is (empty? reminder-after-delete))
        ))))

(deftest test-patch-next-date
  (tdu/init-placeholder-data!)
  (user/create! "user1" "password1")
  (let [token (invoke-login {:username "user1" :password "password1"})
        [_ record] (post-request "/api/thoughts" {:thought "Just a thought"} token)]
    ;; Verify the basics
    (is (string? token))
    (is (map? record))
    ;; On to the tests
    (testing "We can set a nil date for an existing reminder"
      (let [[r-initial initial] (post-request "/api/reminders" {:thought-id (:id record) :type-id "spaced"} token)
            [r-updated r-empty] (patch-request "/api/reminders" (:id initial) {:next-date nil} token)
            [_ updated] (get-request (str "/api/reminders/" (:id initial)) nil token)
            ]
        (is (= 201 (:status r-initial)))
        (is (= 204 (:status r-updated)))
        (is (empty? r-empty))                               ; Patch returns no content
        ;; Both reminders should be the same, other than the next_date
        (is (= (dissoc initial :next_date)
               (dissoc updated :next_date)))
        (is (nil? (:next_date updated)))
        ))
    (testing "We can set a string date for an existing reminder"
      (let [[r-initial initial] (post-request "/api/reminders" {:thought-id (:id record) :type-id "spaced"} token)
            [r-updated r-empty] (patch-request "/api/reminders" (:id initial) {:next-date "2017-01-01"} token)
            [_ updated] (get-request (str "/api/reminders/" (:id initial)) nil token)
            ]
        ;; TODO: Expand tests, just verifying the basics work right now, since the API may change
        (is (= 201 (:status r-initial)))
        (is (= 204 (:status r-updated)))
        (is (empty? r-empty))                               ; Patch returns no content
        ;; Both reminders should be the same, other than the next_date
        (is (= (dissoc initial :next_date)
               (dissoc updated :next_date)))
        (is (= (read-string "#inst \"2017-01-01\"") (:next_date updated)))
        ))
    (testing "Trying to set the date from someone other than the owner fails"
      (let [[r-initial initial] (post-request "/api/reminders" {:thought-id (:id record) :type-id "spaced"} token)
            invalid-token (invoke-login {:username tdu/ph-username :password tdu/ph-password})
            [r-updated r-empty] (patch-request "/api/reminders" (:id initial) {:next-date "2017-01-01"} invalid-token)
            [_ updated] (get-request (str "/api/reminders/" (:id initial)) nil token)
            ]
        (is (= 201 (:status r-initial)))
        (is (= 404 (:status r-updated)))
        (is (empty? r-empty))                               ; Patch returns no content
        ;; Nothing should have changed
        (is (= initial updated))))))

(deftest test-get-active-reminders
  (tdu/init-placeholder-data!)
  ;; Test with a single user
  (user/create! "user1" "password1")
  (let [token         (invoke-login {:username "user1" :password "password1"})
        ;; Test thought and reminders
        [_ thought-1] (post-request "/api/thoughts" {:thought "Just a thought"} token)
        [_ thought-2] (post-request "/api/thoughts" {:thought "Another thought"} token)
        [_ rem-1-1] (post-request "/api/reminders" {:thought-id (:id thought-1) :type-id "spaced"} token)
        [_ rem-1-2] (post-request "/api/reminders" {:thought-id (:id thought-1) :type-id "spaced"} token)
        [_ rem-2] (post-request "/api/reminders" {:thought-id (:id thought-2) :type-id "spaced"} token)
        ;; Some test timestamps
        minus-2s      (c/to-date (t/plus (t/now) (t/seconds -2)))
        minus-1s      (c/to-date (t/plus (t/now) (t/seconds -1)))
        in-10m        (c/to-date (t/plus (t/now) (t/minutes 10)))
        ;; We get the thought description when returning the reminders, so
        ;; let's define a function to remove it
        clear-thought #(dissoc % :thought)
        ]
    ;; Verify the basics
    (is (string? token))
    (doseq [item [thought-1 thought-2 rem-1-1 rem-1-2 rem-2]]
      (is (map? item) (str "Item should be a map " item)))
    ;; On to the tests
    (testing "There are no pending reminders initially"
      (let [[response reminders] (get-request "/api/reminders" nil token)]
        (is (= 200 (:status response)))
        (is (empty? reminders))))
    (testing "A reminder shows up as pending if its next_date is in the past"
      ;; Change the next reminder dates
      (is (= 1 (reminder/update-reminder-date! (:id rem-1-2)
                                               minus-1s
                                               (:properties rem-1-2))))
      (is (= 1 (reminder/update-reminder-date! (:id rem-1-1)
                                               in-10m
                                               (:properties rem-1-1))))
      (let [rem-1-2 (reminder/get-by-id (:id rem-1-2))      ; Reload since we changed the date
            [response r-list] (get-request "/api/reminders" nil token)]
        (is (= 200 (:status response)))
        (is (= [rem-1-2]
               (map clear-thought r-list)))                 ; Reminder list includes the thought
        (is (= (:thought thought-1)
               (:thought (first r-list))))
        ))
    (testing "Reminders are returned in next_date order"
      (is (= 1 (reminder/update-reminder-date! (:id rem-2)
                                               minus-2s
                                               (:properties rem-2))))
      (let [rem-1-2 (reminder/get-by-id (:id rem-1-2))      ; Reload since we changed the date
            rem-2   (reminder/get-by-id (:id rem-2))
            [response r-list] (get-request "/api/reminders" nil token)]
        (is (= 200 (:status response)))
        (is (= [rem-2 rem-1-2]
               (map clear-thought r-list)))                 ; Reminder list includes the thought
        (is (= (map :thought [thought-2 thought-1])
               (map :thought r-list)))))
    (testing "A different user does not get any pending reminders"
      (user/create! "user2" "password")
      (let [other-token (invoke-login {:username "user2" :password "password"})
            [response reminders] (get-request "/api/reminders" nil other-token)]
        (is (string? other-token))
        ;; The call itself succeeded, since it's a valid user, but there are no reminders
        (is (= 200 (:status response)))
        (is (empty? reminders))))
    (testing "An invalid token throws up an error"
      (let [[response result] (get-request "/api/reminders" nil "invalid")]
        ;; The call itself succeeded, since it's a valid user, but there are no reminders
        (is (= 401 (:status response)))
        (is (:error result))))))


(deftest test-mark-as-viewed
  (tdu/init-placeholder-data!)
  ;; Test with a single user
  (user/create! "user1" "password1")
  (let [token         (invoke-login {:username "user1" :password "password1"})
        ;; Test thought and reminders
        [_ thought-1] (post-request "/api/thoughts" {:thought "Just a thought"} token)
        [_ thought-2] (post-request "/api/thoughts" {:thought "Another thought"} token)
        [_ rem-1] (post-request "/api/reminders" {:thought-id (:id thought-1) :type-id "spaced"} token)
        [_ rem-2] (post-request "/api/reminders" {:thought-id (:id thought-2) :type-id "spaced"} token)
        ;; Some test timestamps
        minus-2s      (c/to-date (t/plus (t/now) (t/seconds -2)))
        minus-1s      (c/to-date (t/plus (t/now) (t/seconds -1)))
        ;; Set both reminders to have their next reminder date ready and reload
        _             (reminder/update-reminder-date! (:id rem-1)
                                                      minus-1s
                                                      (:properties rem-1))
        _             (reminder/update-reminder-date! (:id rem-2)
                                                      minus-2s
                                                      (:properties rem-2))
        rem-1         (reminder/get-by-id (:id rem-1))
        rem-2         (reminder/get-by-id (:id rem-2))

        ;; We get the thought description when returning the reminders, so
        ;; let's define a function to remove it
        clear-thought #(dissoc % :thought)]
    ;; Verify the basics
    (is (string? token))
    (doseq [item [thought-1 thought-2 rem-1 rem-2]]
      (is (map? item) (str "Item should be a map " item)))
    ;; On to the tests
    (testing "All reminders are pending initially"
      (let [[response r-list] (get-request "/api/reminders" nil token)]
        (is (= 200 (:status response)))
        (is (= [rem-2 rem-1]
               (map clear-thought r-list)))))
    (testing "Marking a reminder as viewed removes it from the list"
      (let [[response post-result] (post-request (str "/api/reminders/viewed/" (:id rem-1)) nil token)
            [_ r-list] (get-request "/api/reminders" nil token)
            new-rem-1 (reminder/get-by-id (:id rem-1))]
        ;; API call returns what we expect
        (is (= 200 (:status response)))
        (is (= 1 post-result))
        ;; Reminder should no longer show up as pending
        (is (= [rem-2]
               (map clear-thought r-list)))
        ;; The reminder itself was updated
        (is (not= rem-1 new-rem-1))
        (is (= (dissoc rem-1 :next_date :properties)
               (dissoc new-rem-1 :next_date :properties)))
        (is (t/after? (c/to-date-time (:next_date new-rem-1))
                      (c/to-date-time (:next_date rem-1))))
        (is (= 1 (get-in new-rem-1 [:properties :day-idx])))))
    (testing "A different user cannot mark the reminder as viewed"
      (user/create! "user2" "password")
      (let [other-token (invoke-login {:username "user2" :password "password"})
            rem-before  (reminder/get-by-id (:id rem-1))
            [response post-result] (post-request (str "/api/reminders/viewed/" (:id rem-1)) nil other-token)
            rem-after   (reminder/get-by-id (:id rem-1))]
        (is (string? other-token))
        ;; The call itself succeeded, since it's a valid user, but there are no reminders
        (is (= 404 (:status response)))
        (is (empty? post-result))
        (is (= rem-before rem-after))))
    (testing "Calling the function repeatedly keeps moving forward the date and increasing the day index"
      (let [day-count   (count (get-in rem-1 [:properties :days]))
            ;; Will call the function multiple times on purpose to ensure it works even after the reminder has no more repetitions
            time-series (for [i (range (+ 3 day-count))]
                          (let [[response post-result] (post-request (str "/api/reminders/viewed/" (:id rem-1)) nil token)
                                new-rem (reminder/get-by-id (:id rem-1))]
                            {:status    (:status response)
                             :result    post-result
                             :index     i
                             :next_date (:next_date new-rem)
                             :day-idx   (get-in new-rem [:properties :day-idx])}
                            ))]
        (doseq [item time-series]
          ;; We will always return that we updated an existing reminder, even if we didn't move it forward
          (is (= 200 (:status item)))
          (is (= 1 (:result item)))
          (if (< (:day-idx item) day-count)
            (is (some? (:next_date item)) (str "Expected a date on " item))
            (do
              (is (nil? (:next_date item)) (str "Did not expect a date for " item))
              (is (= day-count (:day-idx item)) (str "Did not expect the day index to move after " day-count))))
          )
        ))
    (testing "Marking a legacy reminder as viewed adds a schedule and moves its date forward"
      (let [_         (reminder/update-reminder-date! (:id rem-1)
                                                      minus-1s
                                                      nil)
            legacy    (reminder/get-by-id (:id rem-1))
            [response post-result] (post-request (str "/api/reminders/viewed/" (:id rem-1)) nil token)
            [_ r-list] (get-request "/api/reminders" nil token)
            new-rem-1 (reminder/get-by-id (:id rem-1))]
        ;; Ensure our setup went as expected
        (is (nil? (:properties legacy)))
        (is (= minus-1s (:next_date legacy)))
        ;; API call returns what we expect
        (is (= 200 (:status response)))
        (is (= 1 post-result))
        ;; Reminder does not show up as pending
        (is (= [rem-2]
               (map clear-thought r-list)))
        ;; The call configured the reminder to for spaced repetition and moved the date
        ;; forward, but left it at the first index
        (is (zero? (get-in new-rem-1 [:properties :day-idx])))
        (is (t/after? (c/to-date-time (:next_date new-rem-1))
                      (c/to-date-time minus-1s)))
        (is (< 0 (count (get-in new-rem-1 [:properties :days]))))))
    ))

;; TODO: Tests for
;; - Mark a reminder as expired
;; - Get reminders for a thought