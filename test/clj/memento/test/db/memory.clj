(ns memento.test.db.memory
  (:require [clojure.string :refer [split-lines split]]
            [clojure.set :refer [intersection]]
            [clojure.test :refer :all]
            [yesql.core :refer [defqueries]]
            [memento.db.core :as db]
            [memento.db.memory :as memory]
            [memento.db.user :as user]
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
   (import-placeholder-memories! tdu/ph-username "quotes.txt"))
  ([username]
   (import-placeholder-memories! username "quotes.txt"))
  ([username filename]
   (let [memories (-> (slurp (str test-file-path filename)) (split-lines))]
     (doseq [m memories] (memory/save-memory! {:username username :thought m})))))


;;;;
;;;; Tests
;;;;


(deftest test-save-memory
  (tdu/init-placeholder-data!)
  (let [result (memory/save-memory! {:username tdu/ph-username :thought "Just wondering"})]
    ;; We return only the number of records updated
    (is (= result 1))))


;; Strictly speaking, the following belongs in the tests for db.core, but
;; keeping it here since it's more related to querying.
(deftest test-query-count
  (tdu/init-placeholder-data!)
  (user/create-user! "shortuser" "somepass")
  (import-placeholder-memories!)
  (import-placeholder-memories! "shortuser" "quotes2.txt")
  (testing "Getting an all-memory count returns the total memories"
    (is (= {:count 22} (first (db/get-thought-count {:username tdu/ph-username}))))
    (is (= {:count 5} (first (db/get-thought-count {:username "shortuser"})))))
  (testing "Getting an memory query count returns the count of matching memories"
    (are [count q u] (= {:count count} (first (db/search-thought-count {:username u :query q})))
                     3 "memory" tdu/ph-username
                     0 "memory" "shortuser"
                     4 "people" tdu/ph-username
                     1 "people" "shortuser"
                     0 "creativity|akira" tdu/ph-username
                     3 "creativity|akira" "shortuser"
                     1 "mistake" tdu/ph-username
                     1 "mistake" "shortuser")
    )
  )



(deftest test-query-memories
  (tdu/init-placeholder-data!)
  (testing "Querying an empty database returns no values"
    (let [r (memory/query-memories tdu/ph-username)]
      (is (= 0 (:total r)))
      (is (empty? (:results r)))))
  (testing "Query previous value"
    (let [_        (memory/save-memory! {:username tdu/ph-username :thought "Just wondering"})
          result   (memory/query-memories tdu/ph-username)
          thoughts (:results result)
          thought  (first thoughts)]
      (is (= 1 (count thoughts)))
      (is (= 1 (:total result)))
      (is (= 1 (:pages result)))
      (is (:id thought))
      (is (:created thought))
      (is (= tdu/ph-username (:username thought)))
      (is (= "Just wondering" (:thought thought)))
      ))
  (testing "Test what happens after adding a few memories"
    (let [memories ["A memory" "A second one" "A _somewhat_ longish memory including a bit or *markdown*"]
          _        (doseq [m memories] (memory/save-memory! {:username tdu/ph-username :thought m}))
          result   (memory/query-memories tdu/ph-username)]
      (is (= 4 (count (:results result))))
      (is (= 4 (:total result)))
      (let [texts     (extract-text (:results result))
            to-search (conj memories "Just wondering")]
        (doseq [m to-search]
          (is (u/in-seq? texts m))))))
  (testing "Test querying for a string"
    (let [result (memory/query-memories tdu/ph-username "memory")
          texts  (extract-text (:results result))]
      (is (= 2 (:total result)))
      (is (= 2 (count texts)))
      (doseq [m texts]
        (is (re-seq #"memory" m)))))
  (testing "Confirm words from a similar root are returned"
    (let [result (memory/query-memories tdu/ph-username "memories")
          texts  (extract-text (:results result))]
      (is (= 2 (count texts)))
      (is (= 2 (:total result)))
      (doseq [m texts]
        (is (re-seq #"memo" m))
        )
      ))
  (testing "Wonder is considered a root for wondering"
    (let [result (memory/query-memories tdu/ph-username "wonder")
          texts  (extract-text (:results result))]
      (is (= 1 (count texts)))
      (is (re-seq #"wondering" (first texts)))
      ))
  (testing "Matching is OR by default"
    (let [result (memory/query-memories tdu/ph-username "memories second")
          texts  (extract-text (:results result))]
      (is (= 3 (count texts)))
      (is (= 3 (:total result)))
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
          dates  (map :created (:results result))
          ]
      (is (= 22 (:total result)))
      (is (= 3 (:pages result)))
      (is (= dates (reverse (sort dates))))))
  (testing "Querying with a parameter returns them in descending score order"
    (let [result (memory/query-memories tdu/ph-username "memory")
          scores (map :rank (:results result))
          ]
      (is (= 3 (count scores)))
      (is (= 1 (:pages result)))
      (is (= scores (reverse (sort scores))))
      ))
  (testing "Querying with multiple parameters returns them in descending score order"
    (let [result (memory/query-memories tdu/ph-username "money humor")
          scores (map :rank (:results result))
          ]
      (is (= 5 (count scores)))
      (is (= 1 (:pages result)))
      (is (= scores (reverse (sort scores))))
      )))


(deftest test-pagination
  (tdu/init-placeholder-data!)
  ;; We'll work with a list of thoughts that have a number as their first item
  ;;
  ;; Interesting problem, though...
  ;; We can't rely on the rank order for anything but the top 2 items to be
  ;; consistent with the search data we have, so there's no point in testing
  ;; that we're getting the "right" page.
  ;;
  ;; This will mean we won't get consistent paging.
  (import-placeholder-memories! tdu/ph-username "numbers.txt")
  (let [extract-idx (fn [coll]
                      (->> coll
                           (map #(split % #" "))
                           (map first)
                           (map read-string)))]
    (testing "Querying without any pagination parameters returns the first few memories"
      (let [result   (memory/query-memories tdu/ph-username)
            thoughts (map :thought (:results result))
            indices  (extract-idx thoughts)]
        (is (= 32 (:total result)))
        (is (= 10 (count thoughts)))
        (is (= 4 (:pages result)))
        ;; Thoughts come in inverse date order by default... meaning
        ;; we'll get them in reverse number order
        (is (= indices (reverse (range 23 33))))
        ))
    (testing "Querying with an offset returns the correct memories"
      (let [result   (memory/query-memories tdu/ph-username "" 2)
            thoughts (map :thought (:results result))
            indices  (extract-idx thoughts)]
        (is (= 32 (:total result)))
        (is (= 10 (count thoughts)))
        (is (= 4 (:pages result)))
        ;; Thoughts come in inverse date order by default... meaning
        ;; we'll get them in reverse number order
        (is (= indices (reverse (range 21 31))))
        ))
    (testing "Querying with too far an offset returns fewer records"
      (let [result   (memory/query-memories tdu/ph-username "" 29)
            thoughts (map :thought (:results result))
            indices  (extract-idx thoughts)]
        (is (= 32 (:total result)))
        (is (= 3 (count thoughts)))
        (is (= 4 (:pages result)))
        ;; Thoughts come in inverse date order by default... meaning
        ;; we'll get them in reverse number order
        (is (= indices (reverse (range 1 4))))
        ))
    (testing "Querying with a parameter returns them in descending score order"
      (let [result   (memory/query-memories tdu/ph-username "dreaming memory money people")
            thoughts (map :thought (:results result))
            indices  (extract-idx thoughts)]
        (is (= 10 (count indices)))
        (is (= 13 (:total result)))
        (is (= 2 (:pages result)))
        ;; We know the top 2 ranked items for those terms are 2 & 3, but don't
        ;; know for sure in which order we'll get them... We could compare the
        ;; scores, but float comparison is iffy.
        (is (= (set (take 2 indices)) #{2 3}))
        ))
    (testing "Querying with a parameter and an offset respects both rank and offset"
      (let [result   (memory/query-memories tdu/ph-username "dreaming memory money people" 2)
            thoughts (map :thought (:results result))
            indices  (extract-idx thoughts)]
        (is (= 10 (count indices)))
        (is (= 13 (:total result)))
        ;; We know the top 2 ranked items for those terms are 2 & 3, but don't
        ;; know for sure in which order we'll get them... So we check that the
        ;; top two items aren't in the list, given that we sent an offset of 2.
        (is (empty? (intersection (set indices) #{2 3})))
        ))
    (testing "Querying with a parameter and too far an offset returns fewer records"
      (let [result   (memory/query-memories tdu/ph-username "dreaming memory money people" 11)
            scores (map :rank (:results result))]
        (is (= 13 (:total result)))
        (is (= 2 (count scores)))
        (doseq [i scores]
          ;; This should hold for all values being returned at that offset,
          ;; until the Postgresql scoring algorithm changes
          (is (>= 0.11 i)))
        ))
    ))


