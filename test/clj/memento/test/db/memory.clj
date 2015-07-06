(ns memento.test.db.memory
  (:require [clojure.string :refer [split-lines]]
            [clojure.test :refer :all]
            [yesql.core :refer [defqueries]]
            [memento.db.core :as db]
            [memento.db.memory :as memory]
            [memento.test.db.core :as tdb]
            [memento.test.db.user :as tdu]
            [numergent.utils :as u]))


;;;;
;;;; Definitions
;;;;

(def test-base-path (-> "." java.io.File. .getCanonicalPath))
(def test-file-path (str "file://" test-base-path "/test/files/"))


;;;;
;;;; Helper functions
;;;;

(defn extract-text
  "Receives a collection of query results and returns the :text value for each"
  [coll]
  (map :thought coll))


(defn import-placeholder-memories!
  "Imports a series of placehoder memories from a file of quotes"
  ([]
   (import-placeholder-memories! tdu/ph-username))
  ([username]
   (let [memories (-> (slurp (str test-file-path "quotes.txt")) (split-lines))]
     (doseq [m memories] (memory/save-memory! {:username username :thought m})))))


;;;;
;;;; Tests
;;;;


(deftest test-save-memory
  (tdu/init-placeholder-data!)
  (let [result (memory/save-memory! {:username tdu/ph-username :thought "Just wondering"})]
    ;; We return only the number of records updated
    (is (= result 1))))


(deftest test-query-memories
  (tdu/init-placeholder-data!)
  (testing "Querying an empty database returns no values"
    (is (empty? (memory/query-memories tdu/ph-username))))
  (testing "Query previous value"
    (let [_        (memory/save-memory! {:username tdu/ph-username :thought "Just wondering"})
          thoughts (memory/query-memories tdu/ph-username)
          thought  (first thoughts)]
      (is (= 1 (count thoughts)))
      (is (:id thought))
      (is (:created thought))
      (is (= tdu/ph-username (:username thought)))
      (is (= "Just wondering" (:thought thought)))
      ))
  (testing "Test what happens after adding a few memories"
    (let [memories ["A memory" "A second one" "A _somewhat_ longish memory including a bit or *markdown*"]
          _        (doseq [m memories] (memory/save-memory! {:username tdu/ph-username :thought m}))
          result   (memory/query-memories tdu/ph-username)]
      (is (= 4 (count result)))
      (let [texts     (extract-text result)
            to-search (conj memories "Just wondering")]
        (doseq [m to-search]
          (is (u/in-seq? texts m))))))
  (testing "Test querying for a string"
    (let [result (memory/query-memories tdu/ph-username "memory")
          texts  (extract-text result)]
      (is (= 2 (count result)))
      (is (= 2 (count texts)))
      (doseq [m texts]
        (is (re-seq #"memory" m)))))
  (testing "Confirm words from a similar root are returned"
    (let [result (memory/query-memories tdu/ph-username "memories")
          texts  (extract-text result)]
      (is (= 2 (count result)))
      (is (= 2 (count texts)))
      (doseq [m texts]
        (is (re-seq #"memo" m))
        )
      ))
  (testing "Wonder is considered a root for wondering"
    (let [result (memory/query-memories tdu/ph-username  "wonder")
          texts  (extract-text result)]
      (is (= 1 (count texts)))
      (is (re-seq #"wondering" (first texts)))
      ))
  (testing "Matching is OR by default"
    (let [result (memory/query-memories tdu/ph-username "memories second")
          texts  (extract-text result)]
      (is (= 3 (count result)))
      (doseq [m texts]
        (is (or (re-seq #"memory" m)
                (re-seq #"second" m)))
        )
      )))


(deftest test-query-sort-order
  (tdu/init-placeholder-data!)
  (import-placeholder-memories!)
  (testing "Querying without a parameter returns them in inverse date order"
    (let [result (memory/query-memories tdu/ph-username)
          dates  (map :created result)
          ]
      (is (= 22 (count result)))
      (is (= dates (reverse (sort dates))))))
  (testing "Querying with a parameter returns them in descending score order"
    (let [result (memory/query-memories tdu/ph-username "memory")
          scores (map :rank result)
          ]
      (is (= 3 (count result)))
      (is (= scores (reverse (sort scores))))
      ))
  (testing "Querying with multiple parameters returns them in descending score order"
    (let [result (memory/query-memories tdu/ph-username "money humor")
          scores (map :rank result)
          ]
      (is (= 5 (count result)))
      (is (= scores (reverse (sort scores))))
      )))