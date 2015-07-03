(ns memento.test.db.memory
  (:require [clojure.string :refer [split-lines]]
            [clojure.test :refer :all]
            [yesql.core :refer [defqueries]]
            [memento.db.core :as db]
            [memento.db.memory :as memory]
            [memento.test.db.core :as tdb]
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


;;;;
;;;; Tests
;;;;


(deftest test-save-memory
  (tdb/init-placeholder-data!)
  (let [result (memory/save-memory! {:thought "Just wondering"})]
    ;; We return only the number of records updated
    (is (= result 1))))


(deftest test-query-memories
  (tdb/init-placeholder-data!)
  (testing "Querying an empty database returns no values"
    (is (empty? (memory/query-memories))))
  (testing "Query previous value"
    (let [_        (memory/save-memory! {:thought "Just wondering"})
          thoughts (memory/query-memories)
          thought  (first thoughts)]
      (is (= 1 (count thoughts)))
      (is (:id thought))
      (is (:created thought))
      (is (= tdb/default-test-user (:username thought)))
      (is (= "Just wondering" (:thought thought)))
      ))
  (testing "Test what happens after adding a few memories"
    (let [memories ["A memory" "A second one" "A _somewhat_ longish memory including a bit or *markdown*"]
          _        (doseq [m memories] (memory/save-memory! {:thought m}))
          result   (memory/query-memories)]
      (is (= 4 (count result)))
      (let [texts     (extract-text result)
            to-search (conj memories "Just wondering")]
        (doseq [m to-search]
          (is (u/in-seq? texts m))))))
  (testing "Test querying for a string"
    (let [result (memory/query-memories "memory")
          texts  (extract-text result)]
      (is (= 2 (count result)))
      (is (= 2 (count texts)))
      (doseq [m texts]
        (is (re-seq #"memory" m)))))
  (testing "Confirm words from a similar root are returned"
    (let [result (memory/query-memories "memories")
          texts  (extract-text result)]
      (is (= 2 (count result)))
      (is (= 2 (count texts)))
      (doseq [m texts]
        (is (re-seq #"memo" m))
        )
      ))
  (testing "Wonder is considered a root for wondering"
    (let [result (memory/query-memories "wonder")
          texts  (extract-text result)]
      (is (= 1 (count texts)))
      (is (re-seq #"wondering" (first texts)))
      ))
  (testing "Matching is OR by default"
    (let [result (memory/query-memories "memories second")
          texts  (extract-text result)]
      (is (= 3 (count result)))
      (doseq [m texts]
        (is (or (re-seq #"memory" m)
                (re-seq #"second" m)))
        )
      )))


(deftest test-query-sort-order
  (tdb/init-placeholder-data!)
  (let [memories (-> (slurp (str test-file-path "quotes.txt")) (split-lines))
        _        (doseq [m memories] (memory/save-memory! {:thought m}))
        ]
    (is memories)
    (testing "Querying without a parameter returns them in inverse date order"
      (let [result (memory/query-memories)
            dates  (map :created result)
            ]
        (is (= 22 (count result)))
        (is (= dates (reverse (sort dates))))))
    (testing "Querying with a parameter returns them in descending score order"
      (let [result (memory/query-memories "memory")
            scores  (map :rank result)
            ]
        (is (= 3 (count result)))
        (is (= scores (reverse (sort scores))))
        ))
    (testing "Querying with multiple parameters returns them in descending score order"
      (let [result (memory/query-memories "money humor")
            scores  (map :rank result)
            ]
        (is (= 5 (count result)))
        (is (= scores (reverse (sort scores))))
        ))))