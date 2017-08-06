(ns memento.test.db.reminder
  (:require [clojure.test :refer :all]
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
  (let [memory (memory/create-memory! {:username tdu/ph-username :thought "Just wondering"})
        result (reminder/create! {:thought_id (:id memory) :type_id "spaced"})]
    (is (map? result))
    (is (:id result))
    (is (:created result))
    (is (= "spaced" (:type_id result)))
    (is (:next_date result))
    (is (= 4 (count (get-in result [:properties :schedule]))))
    (is (= (:id memory) (:thought_id result)))))


