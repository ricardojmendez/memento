(ns memento.test.db
  (:require [clojure.test :refer :all]
            [memento.db :as db]
            [numergent.utils :as u]))


(def default-test-user "ricardo")

(def conn (db/get-connection))


(defn extract-text
  "Receives a collection of query results and returns the :text value for each"
  [coll]
  (map #(get-in % [:_source :text]) coll))

(deftest test-save-memory
  (db/initialize-index! conn)
  (let [result (db/save-memory! conn {:text "Just wondering"})]
    (is (= "memento-test" (:index result)))
    (is (not-empty (:id result)))
    (is (= "memory" (:type result)))
    (is (= 1 (:version result)))))

(deftest test-query-memories
  (db/initialize-index! conn)
  (testing "Querying an empty index returns no values"
    (is (empty? (db/query-memories conn))))
  (testing "Query previous value"
    (let [_      (db/save-memory! conn {:text "Just wondering"})
          _      (db/flush-index! conn)
          result (db/query-memories conn)]
      (is (= 1 (count result)))
      (is (= "Just wondering" (get-in (first result) [:_source :text])))
      (is (= default-test-user (get-in (first result) [:_source :username])))
      (is result)
      ))
  (testing "Test what happens after adding a few memories"
    (let [memories ["A memory" "A second one" "A _somewhat_ longish memory including a bit or *markdown*"]
          _        (doseq [m memories] (db/save-memory! conn {:text m}))
          _        (db/flush-index! conn)
          result   (db/query-memories conn)]
      (is (= 4 (count result)))
      (let [texts     (extract-text result)
            to-search (conj memories "Just wondering")]
        (doseq [m to-search]
          (is (u/in-seq? texts m)))
        )))
  (testing "Test querying for a string"
    (let [result (db/query-memories conn "memory")
          texts  (extract-text result)]
      (is (= 2 (count result)))
      (is (= 2 (count texts)))
      (doseq [m texts]
        (is (re-seq #"memory" m))
        )
      ))
  (testing "Confirm words from a similar root are returned"
    (let [result (db/query-memories conn "memories")
          texts  (extract-text result)]
      (is (= 2 (count result)))
      (is (= 2 (count texts)))
      (doseq [m texts]
        (is (re-seq #"memo" m))
        )
      ))
  (testing "Wonder is considered a root for wondering"
    (let [result (db/query-memories conn "wonder")
          texts  (extract-text result)]
      (is (= 1 (count texts)))
      (is (re-seq #"wondering" (first texts)))
      ))
  (testing "Matching is OR by default"
    (let [result (db/query-memories conn "memories second")
          texts  (extract-text result)]
      (is (= 3 (count result)))
      (doseq [m texts]
        (is (or (re-seq #"memory" m)
                (re-seq #"second" m)))
        )
      ))

  )
