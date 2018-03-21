(ns memento.test.db.reminder
  (:require [clojure.test :refer :all]
            [clj-time.coerce :as coerce]
            [clj-time.local :as l]
            [memento.db.core :refer [*db*] :as db]
            [memento.db.reminder :as reminder]
            [memento.db.user :as user]
            [memento.db.memory :as memory]
            [memento.test.db.core :as tdb]
            [memento.test.db.user :as tdu]
            [memento.test.db.memory :as tdm]
            [mount.core :as mount]))


(use-fixtures
  :once
  (fn [f]
    (mount/start
      #'memento.config/env
      #'memento.db.core/*db*)
    (f)))

;;;
;;; Creation
;;;

(deftest test-create-reminder
  (tdu/init-placeholder-data!)
  (let [memory (memory/create! {:username tdu/ph-username :thought "Just wondering"})
        result (reminder/create! {:thought-id (:id memory) :type-id "spaced"})]
    (is (map? result))
    (is (:id result))
    (is (:created result))
    (is (= "spaced" (:type-id result)))
    (is (:next-date result))
    (is (= 4 (count (get-in result [:properties :days]))))
    (is (zero? (get-in result [:properties :day-idx])))
    (is (= (:id memory) (:thought-id result)))))


;;;
;;; Getting reminders by id
;;;

(deftest test-get-reminder
  (tdu/init-placeholder-data!)
  (let [memory  (memory/create! {:username tdu/ph-username :thought "Just wondering"})
        created (reminder/create! {:thought-id (:id memory) :type-id "spaced"})
        another (reminder/create! {:thought-id (:id memory) :type-id "once" :next-date (coerce/to-date (l/local-now))})
        result  (reminder/get-by-id (:id created))]
    (is (map? result))
    (is (:id result))
    (is (= created (dissoc result :username)))              ; Getting a reminder also returns its owner, creating it does not
    (is (not= (:id result) (:id another)))))

(deftest test-get-if-owner
  (tdu/init-placeholder-data!)
  (let [memory  (memory/create! {:username tdu/ph-username :thought "Just wondering"})
        created (reminder/create! {:thought-id (:id memory) :type-id "spaced"})
        item    (reminder/get-if-owner tdu/ph-username (:id created))
        invalid (reminder/get-if-owner "someone-else" (:id created))]
    (is (nil? invalid))
    (is (some? item))
    (is (= created (dissoc item :username)))))

;;;
;;; Getting active reminders for a thought
;;;

(deftest test-active-reminders-for-thought
  (tdu/init-placeholder-data!)
  (testing "We get only one reminder when there's one on the database"
    (let [thought   (memory/create! {:username tdu/ph-username :thought "Just wondering"})
        created   (reminder/create! {:thought-id (:id thought) :type-id "spaced"})
        reminders (db/get-active-reminders-for-thought {:id (:id thought) :username tdu/ph-username})]
    (is (= [created] reminders))))
  (testing "We get only one reminder even when a different thought has other reminders"
    (let [thought   (memory/create! {:username tdu/ph-username :thought "Wondering again"})
          created   (reminder/create! {:thought-id (:id thought) :type-id "spaced"})
          reminders (db/get-active-reminders-for-thought {:id (:id thought) :username tdu/ph-username})]
      (is (= [created] reminders)))))