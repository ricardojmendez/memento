(ns memento.test.db.memory
  (:require [clojure.string :refer [split-lines split]]
            [clojure.set :refer [intersection]]
            [clojure.test :refer :all]
            [clj-time.coerce :as c]
            [clj-time.core :as t]
            [memento.db.core :refer [*db*] :as db]
            [memento.db.memory :as memory]
            [memento.db.user :as user]
            [memento.test.db.core :as tdb]
            [memento.test.db.user :as tdu]
            [numergent.utils :as u]
            ))


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
     (doseq [m memories]
       (memory/create-memory! {:username username :thought m})
       ; We have some tests which ensure thoughts are returned by creation date,
       ; so let's give at least one millisecond in between thought timestamps
       (Thread/sleep 1)))))


(defn extract-thought-idx
  "Receives what it expects to be a collection of thought lines, each one starting
  with a value that can be converted to a number (likely an integer)"
  [coll]
  (->> coll
       (map #(split % #" "))
       (map first)
       (map read-string)))

;;;;
;;;; Tests
;;;;


;;;
;;; Status overview
;;;


(deftest test-set-memory-status
  (is (= :open (:status (memory/set-memory-status {:created (c/to-date (t/now))}))))
  (is (= :open (:status (memory/set-memory-status {:created (c/to-date (.minusHours (t/now) 1))}))))
  (is (= :closed (:status (memory/set-memory-status {:created (c/to-date (.minusMillis (t/now) memory/open-duration))}))))
  (is (= :closed (:status (memory/set-memory-status {:created (c/to-date (.minusYears (t/now) 1))}))))
  )


;;;
;;; Saving
;;;

(deftest test-save-memory
  (tdu/init-placeholder-data!)
  (let [result (memory/create-memory! {:username tdu/ph-username :thought "Just wondering"})]
    ;; We return only the number of records updated
    (is (map? result))
    (is (:id result))
    (is (:created result))
    (is (= "Just wondering" (:thought result)))
    (is (= tdu/ph-username (:username result)))))


(deftest test-save-memory-refine
  (tdu/init-placeholder-data!)
  (let [_      (memory/create-memory! {:username tdu/ph-username :thought "Just wondering"})
        m1     (first (:results (memory/query-memories tdu/ph-username)))
        _      (memory/create-memory! {:username tdu/ph-username :thought "Second memory" :refine_id (:id m1)})
        m2     (first (:results (memory/query-memories tdu/ph-username)))
        _      (memory/create-memory! {:username tdu/ph-username :thought "Third memory" :refine_id (:id m2)})
        m3     (first (:results (memory/query-memories tdu/ph-username)))
        m1r    (last (:results (memory/query-memories tdu/ph-username))) ; Memories are returned in reverse date order on the default query
        _      (memory/create-memory! {:username tdu/ph-username :thought "Unrelated memory, not for thread"})
        all    (memory/query-memories tdu/ph-username)
        thread (memory/query-memory-thread (:root_id m3))]
    ;; First memory has on refine_id nor root_id
    (is m1)
    (is (nil? (:root_id m1)))
    (is (nil? (:refine_id m1)))
    ;; First time we refine a memory, both refine_id and root_id point to the same
    (is m2)
    (is (= (:id m1) (:root_id m2)))
    (is (= (:id m1) (:refine_id m2)))
    ;; If we refine an already-refined memory, the root points to the initial item
    (is m2)
    (is (= (:id m1) (:root_id m3)))
    (is (= (:id m2) (:refine_id m3)))
    ;; After refining, m1 has the root_id assigned to itself
    (is (= (:id m1r) (:root_id m1r)))
    (is (= (:id m1r) (:id m1)))
    ;; Check the thread
    (is thread)
    ; We have four memories for the user
    (is (= 4 (count (:results all))))
    ; Thread only includes three
    (is (= 3 (count thread)))
    ; Thread includes the updated record for the first memory
    (is (= [m1r m2 m3] thread))
    ))


;;;
;;; Querying
;;;


;; Strictly speaking, the following belongs in the tests for db.core, but
;; keeping it here since it's more related to querying.
(deftest test-query-count
  (tdu/init-placeholder-data!)
  (user/create-user! "shortuser" "somepass")
  (import-placeholder-memories!)
  (import-placeholder-memories! "shortuser" "quotes2.txt")
  (testing "Getting an all-memory count returns the total memories"
    (is (= {:count 22} (db/get-thought-count *db* {:username tdu/ph-username})))
    (is (= {:count 5} (db/get-thought-count *db* {:username "shortuser"}))))
  (testing "Getting an memory query count returns the count of matching memories"
    (are [count q u] (= {:count count} (db/search-thought-count *db* {:username u :query q}))
                     3 "memory" tdu/ph-username
                     0 "memory" "shortuser"
                     4 "people" tdu/ph-username
                     1 "people" "shortuser"
                     0 "creativity|akira" tdu/ph-username
                     3 "creativity|akira" "shortuser"
                     1 "mistake" tdu/ph-username
                     1 "mistake" "shortuser")))


(deftest test-query-memories
  (tdu/init-placeholder-data!)
  (testing "Querying an empty database returns no values"
    (let [r (memory/query-memories tdu/ph-username)]
      (is (= 0 (:total r)))
      (is (empty? (:results r)))))
  (testing "Query previous value"
    (let [_        (memory/create-memory! {:username tdu/ph-username :thought "Just wondering"})
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
          _        (doseq [m memories] (memory/create-memory! {:username tdu/ph-username :thought m}))
          result   (memory/query-memories tdu/ph-username)]
      (is (= 4 (count (:results result))))
      (is (= 4 (:total result)))
      (let [texts     (extract-text (:results result))
            to-search (conj memories "Just wondering")]
        (doseq [m to-search]
          (is (u/in-seq? texts m))))
      ;; All items are considered open, since we just created them
      (is (= 4 (count (filter #(= :open (:status %)) (:results result)))))))
  (testing "Test querying for a string"
    (let [result (memory/query-memories tdu/ph-username "memory")
          texts  (extract-text (:results result))]
      (is (= 2 (:total result)))
      (is (= 2 (count texts)))
      ;; All items are considered open, since we just created them
      (is (= 2 (count (filter #(= :open (:status %)) (:results result)))))
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
      (is (= (reverse (sort scores)) scores))
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
  (import-placeholder-memories! tdu/ph-username "numbers.txt")
  ;; This will mean we won't get consistent paging.
  (testing "Querying without any pagination parameters returns the first few memories"
    (let [result   (memory/query-memories tdu/ph-username)
          thoughts (map :thought (:results result))
          indices  (extract-thought-idx thoughts)]
      (is (= 43 (:total result)))
      (is (= 10 (count thoughts)))
      (is (= 5 (:pages result)))
      ;; Thoughts come in inverse date order by default... meaning
      ;; we'll get them in reverse number order
      (is (= indices (reverse (range 34 44))))
      ))
  (testing "Querying with an offset returns the correct memories"
    (let [result   (memory/query-memories tdu/ph-username "" 2)
          thoughts (map :thought (:results result))
          indices  (extract-thought-idx thoughts)]
      (is (= 43 (:total result)))
      (is (= 10 (count thoughts)))
      (is (= 5 (:pages result)))
      ;; Thoughts come in inverse date order by default... meaning
      ;; we'll get them in reverse number order
      (is (= indices (reverse (range 32 42))))
      ))
  (testing "Querying with too far an offset returns fewer records"
    (let [result   (memory/query-memories tdu/ph-username "" 39)
          thoughts (map :thought (:results result))
          indices  (extract-thought-idx thoughts)]
      (is (= 43 (:total result)))
      (is (= 4 (count thoughts)))
      (is (= 5 (:pages result)))
      ;; Thoughts come in inverse date order by default... meaning
      ;; we'll get them in reverse number order
      (is (= indices (reverse (range 1 5))))
      ))
  (testing "Querying with a parameter returns them in descending score order"
    (let [result   (memory/query-memories tdu/ph-username "dreaming memory money people")
          thoughts (map :thought (:results result))
          indices  (extract-thought-idx thoughts)]
      (is (= 10 (count indices)))
      (is (= 22 (:total result)))
      (is (= 3 (:pages result)))
      ;; We know the top 2 ranked items for those terms are 36 & 41, but don't
      ;; know for sure in which order we'll get them... We could compare the
      ;; scores, but float comparison is iffy.
      (is (= (set (take 2 indices)) #{36 41}))
      ))
  (testing "Querying with a parameter and an offset respects both rank and offset"
    (let [result   (memory/query-memories tdu/ph-username "dreaming memory money people" 2)
          thoughts (map :thought (:results result))
          indices  (extract-thought-idx thoughts)]
      (is (= 10 (count indices)))
      (is (= 22 (:total result)))
      ;; We know the top 2 ranked items for those terms are 2 & 3, but don't
      ;; know for sure in which order we'll get them... So we check that the
      ;; top two items aren't in the list, given that we sent an offset of 2.
      (is (empty? (intersection (set indices) #{36 41})))
      ))
  (testing "Querying with a parameter and too far an offset returns fewer records"
    (let [result (memory/query-memories tdu/ph-username "dreaming memory money people" 20)
          scores (map :rank (:results result))]
      (is (= 22 (:total result)))
      (is (= 2 (count scores)))
      (doseq [i scores]
        ;; This should hold for all values being returned at that offset,
        ;; until the Postgresql scoring algorithm changes
        (is (>= 0.21 i)))
      )))



;;;
;;; Memory update and delete
;;;

(deftest test-can-update-memory
  (testing "Lexemes are updated along with the memory"
    (tdu/init-placeholder-data!)
    (let [_         (memory/create-memory! {:username tdu/ph-username :thought "Just wondering"})
          m1        (first (:results (memory/query-memories tdu/ph-username "wondering")))
          updated   (memory/update-memory! (assoc m1 :thought "Different text"))
          ;; Ensure that we didn't leave the lexeme table as it was by querying for the
          ;; old search term and the new one
          wondering (first (:results (memory/query-memories tdu/ph-username "wondering")))
          different (first (:results (memory/query-memories tdu/ph-username "different")))
          all       (memory/query-memories tdu/ph-username)]
      ;; Pre-update values
      (is m1)
      (is (= "Just wondering" (:thought m1)))
      ;; Check the post-update values
      (is updated)
      (is (= (:id m1) (:id updated)))
      (is (= "Different text" (:thought updated)))
      ;; Check we updated the lexemes
      (is (nil? wondering))
      (is different)
      (is (= "Different text" (:thought different)))
      (is (= (:id m1) (:id different)))
      (is (= 1 (count (:results all))))
      ))
  (testing "Cannot update closed thoughts"
    (tdu/init-placeholder-data!)
    (let [_       (memory/create-memory! {:username tdu/ph-username :thought "Just wondering"})
          m1      (first (:results (memory/query-memories tdu/ph-username)))
          ;; Force the date as if we created it a while ago
          _       (tdb/update-thought-created! *db* (assoc m1 :created (c/to-date (.minusMillis (t/now) memory/open-duration))))
          updated (memory/update-memory! (assoc m1 :thought "Different text"))
          m2      (first (:results (memory/query-memories tdu/ph-username)))]
      (is (empty? updated))
      (is (= 0 (:total (memory/query-memories tdu/ph-username "text"))))
      (is (= 1 (:total (memory/query-memories tdu/ph-username "wondering"))))
      (is (= :closed (:status m2)))))
  )

(deftest test-can-update-memory
  (testing "We can delete open thoughts"
    (tdu/init-placeholder-data!)
    (let [_         (memory/create-memory! {:username tdu/ph-username :thought "Just wondering"})
          m1        (first (:results (memory/query-memories tdu/ph-username "wondering")))
          result    (memory/delete-memory! (:id m1))
          after-del (memory/load-memory (:id m1))
          ;; Ensure that we didn't leave the lexeme table as it was by querying for the
          ;; old search term and the new one
          wondering (first (:results (memory/query-memories tdu/ph-username "wondering")))
          all       (memory/query-memories tdu/ph-username)]
      ;; Pre-update values
      (is m1)
      (is (= "Just wondering" (:thought m1)))
      ;; Check that we did not get anything after removing it
      (is (= 1 result))
      (is (nil? after-del))
      ;; Verify we can't re-delete
      (is (= 0 (memory/delete-memory! (:id m1))))
      ;; Check we updated the lexemes
      (is (nil? wondering))
      (is (= {:total 0 :pages 0 :results []} all))
      ))
  (testing "Cannot delete closed thoughts"
    (tdu/init-placeholder-data!)
    (let [_       (memory/create-memory! {:username tdu/ph-username :thought "Just wondering"})
          m1      (first (:results (memory/query-memories tdu/ph-username)))
          ;; Force the date as if we created it a while ago
          _       (tdb/update-thought-created! *db* (assoc m1 :created (c/to-date (.minusMillis (t/now) memory/open-duration))))
          deleted (memory/delete-memory! (:id m1))
          m2      (memory/load-memory (:id m1))]
      (is (= 0 deleted))
      (is (= (select-keys m1 [:id :username :thought])
             (select-keys m2 [:id :username :thought])))
      (is (= 0 (:total (memory/query-memories tdu/ph-username "text"))))
      (is (= 1 (:total (memory/query-memories tdu/ph-username "wondering"))))
      (is (= :closed (:status m2)))))
  )
