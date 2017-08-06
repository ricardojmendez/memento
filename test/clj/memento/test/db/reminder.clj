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
            [memento.test.db.memory :as tdm]))



;;;
;;; Saving
;;;

(deftest test-save-reminder
  (tdu/init-placeholder-data!)
  (let [memory (memory/create! {:username tdu/ph-username :thought "Just wondering"})
        result (reminder/create! {:thought_id (:id memory) :type_id "spaced"})]
    (is (map? result))
    (is (:id result))
    (is (:created result))
    (is (= "spaced" (:type_id result)))
    (is (:next_date result))
    (is (= 4 (count (get-in result [:properties :schedule]))))
    (is (= (:id memory) (:thought_id result)))))


(deftest test-get-reminder
  (tdu/init-placeholder-data!)
  (let [memory  (memory/create! {:username tdu/ph-username :thought "Just wondering"})
        created (reminder/create! {:thought_id (:id memory) :type_id "spaced"})
        another (reminder/create! {:thought_id (:id memory) :type_id "once" :next_date (coerce/to-date (l/local-now)) })
        result  (reminder/get-by-id (:id created))]
    (is (map? result))
    (is (:id result))
    (is (= created (dissoc result :username)))              ; Getting a reminder also returns its owner
    (is (not= (:id result) (:id another)))))