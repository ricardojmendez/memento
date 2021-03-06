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
            [mount.core :as mount]))


(use-fixtures
  :once
  (fn [f]
    (mount/start
      #'memento.config/env
      #'memento.db.core/*db*)
    (f)))

;;;;
;;;; Definitions
;;;;

(def test-base-path (-> "." java.io.File. .getCanonicalPath))
(def test-file-path (str "file://" test-base-path "/test/files/"))

(def empty-query {:total 0 :pages 0 :current-page 0 :results []})

;;;;
;;;; Helper functions
;;;;

(defn extract-text
  "Receives a collection of query results and returns the :text value for each"
  [coll]
  (map :thought coll))


(defn import-placeholder-memories!
  "Imports a series of placeholder memories from a file of quotes"
  ([]
   (import-placeholder-memories! tdu/ph-username "quotes.txt"))
  ([username]
   (import-placeholder-memories! username "quotes.txt"))
  ([username filename]
   (let [memories (-> (slurp (str test-file-path filename)) (split-lines))]
     (doseq [m memories]
       (memory/create! {:username username :thought m})
       ; We have some tests which ensure thoughts are returned by creation date,
       ; so let's give at least one millisecond in between thought timestamps
       (Thread/sleep 1)))))


(defn extract-thought-idx
  "Receives what it expects to be a collection of thought lines, each one starting
  with a value that can be converted to a number (likely an integer)

  Indices will start with 1 because that's how the text file is."
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
  (is (= :open (:status (memory/set-status {:created (c/to-date (t/now))}))))
  (is (= :open (:status (memory/set-status {:created (c/to-date (.minusHours (t/now) 1))}))))
  (is (= :closed (:status (memory/set-status {:created (c/to-date (.minusMillis (t/now) memory/open-duration))}))))
  (is (= :closed (:status (memory/set-status {:created (c/to-date (.minusYears (t/now) 1))}))))
  )


;;;
;;; Saving
;;;

(deftest test-save-memory
  (tdu/init-placeholder-data!)
  (let [result (memory/create! {:username tdu/ph-username :thought "Just wondering"})]
    ;; We return only the number of records updated
    (is (map? result))
    (is (:id result))
    (is (:created result))
    (is (= "Just wondering" (:thought result)))
    (is (= tdu/ph-username (:username result)))))

(deftest test-get-memory
  (tdu/init-placeholder-data!)
  (testing "Test if we can get memories by id"
    (let [created (memory/create! {:username tdu/ph-username :thought "Just wondering"})
          loaded  (memory/get-by-id (:id created))]
      ;; We return only the number of records updated
      (is (map? loaded))
      (is (= created loaded))
      (is (= "Just wondering" (:thought loaded)))
      (is (= tdu/ph-username (:username loaded)))))
  (testing "Test get-if-owner"
    (let [created (memory/create! {:username tdu/ph-username :thought "Just wondering again"})
          loaded  (memory/get-if-owner tdu/ph-username (:id created))
          invalid (memory/get-if-owner "some-else" (:id created))]
      ;; We return only the number of records updated
      (is (map? loaded))
      (is (= created loaded))
      (is (= tdu/ph-username (:username loaded)))
      (is (nil? invalid)))))

(deftest test-save-memory-refine
  (tdu/init-placeholder-data!)
  (let [_      (memory/create! {:username tdu/ph-username :thought "Just wondering"})
        m1     (first (:results (memory/query tdu/ph-username)))
        _      (memory/create! {:username tdu/ph-username :thought "Second memory" :follow-id (:id m1)})
        m2     (first (:results (memory/query tdu/ph-username)))
        _      (memory/create! {:username tdu/ph-username :thought "Third memory" :follow-id (:id m2)})
        m3     (first (:results (memory/query tdu/ph-username)))
        m1r    (last (:results (memory/query tdu/ph-username))) ; Memories are returned in reverse date order on the default query
        _      (memory/create! {:username tdu/ph-username :thought "Unrelated memory, not for thread"})
        all    (memory/query tdu/ph-username)
        thread (memory/query-thread (:root-id m3))]
    ;; First memory has on refine_id nor root_id
    (is m1)
    (is (nil? (:root-id m1)))
    (is (nil? (:follow-id m1)))
    ;; First time we refine a memory, both refine_id and root_id point to the same
    (is m2)
    (is (= (:id m1) (:root-id m2)))
    (is (= (:id m1) (:follow-id m2)))
    ;; If we refine an already-refined memory, the root points to the initial item
    (is m2)
    (is (= (:id m1) (:root-id m3)))
    (is (= (:id m2) (:follow-id m3)))
    ;; After refining, m1 has the root_id assigned to itself
    (is (= (:id m1r) (:root-id m1r)))
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
;;; Filtering
;;;


(deftest test-filter-ownership
  (tdu/init-placeholder-data!)
  (import-placeholder-memories!)
  (user/create! "shortuser" "somepass")
  (let [query    (memory/query tdu/ph-username)
        thoughts (:results query)]
    (testing "Querying with the wrong user doesn't return any thought ids"
      (is (empty? (db/filter-thoughts-owner {:username "shortuser" :thought-ids (map :id thoughts)}))))
    (testing "Querying with the owner returns all the correct ids"
      (let [ids (map :id thoughts)]
        (is (= (set ids)
               (set (map :id (db/filter-thoughts-owner {:username tdu/ph-username :thought-ids (map :id thoughts)})))))))
    (testing "A user can only see his own thoughts"
      (let [other-ids (map :id thoughts)
            t1        (memory/create! {:username "shortuser" :thought "To archive 1"})
            t2        (memory/create! {:username "shortuser" :thought "To archive 2"})
            own-ids   (set [(:id t1) (:id t2)])]
        (is (= own-ids
               (set (map :id (db/filter-thoughts-owner {:username    "shortuser"
                                                        :thought-ids (concat other-ids
                                                                             own-ids)})))))))))


;;;
;;; Querying
;;;


(deftest test-query-count
  (tdu/init-placeholder-data!)
  (user/create! "shortuser" "somepass")
  (import-placeholder-memories!)
  (import-placeholder-memories! "shortuser" "quotes2.txt")
  (testing "Getting an all-memory count returns the total memories"
    (is (= {:count 22} (db/get-thought-count *db* {:username tdu/ph-username :all? false :extra-joins nil})))
    (is (= {:count 5} (db/get-thought-count *db* {:username "shortuser" :all? false :extra-joins nil}))))
  (testing "Getting an memory query count returns the count of matching memories"
    (are [count q u] (= {:count count} (db/search-thought-count *db* {:username u :query q :all? false :extra-joins nil}))
                     3 "memory" tdu/ph-username
                     0 "memory" "shortuser"
                     4 "people" tdu/ph-username
                     1 "people" "shortuser"
                     0 "creativity|akira" tdu/ph-username
                     3 "creativity|akira" "shortuser"
                     1 "mistake" tdu/ph-username
                     1 "mistake" "shortuser"))
  (testing "Verify filtering options for archiving thoughts"
    (let [to-archive-1 (memory/create! {:username "shortuser" :thought "To archive 1"})
          to-archive-2 (memory/create! {:username "shortuser" :thought "To archive 2"})]
      ;; Verify that the thought count includes them both
      (is (= {:count 7} (db/get-thought-count *db* {:username "shortuser" :all? false :extra-joins nil})))
      (is (= {:count 2} (db/search-thought-count *db* {:username "shortuser" :query "archive" :all? false :extra-joins nil})))
      ;; Archive a thought, verify
      (memory/archive! (assoc to-archive-1 :archived? true))
      (is (= {:count 6} (db/get-thought-count *db* {:username "shortuser" :all? false :extra-joins nil})))
      (is (= {:count 1} (db/search-thought-count *db* {:username "shortuser" :query "archive" :all? false :extra-joins nil})))
      (is (= {:count 7} (db/get-thought-count *db* {:username "shortuser" :all? true :extra-joins nil})))
      (is (= {:count 2} (db/search-thought-count *db* {:username "shortuser" :query "archive" :all? true :extra-joins nil})))
      ;; Archive the second one, verify
      (memory/archive! (assoc to-archive-2 :archived? true))
      (is (= {:count 5} (db/get-thought-count *db* {:username "shortuser" :all? false :extra-joins nil})))
      (is (= {:count 0} (db/search-thought-count *db* {:username "shortuser" :query "archive" :all? false :extra-joins nil})))
      (is (= {:count 7} (db/get-thought-count *db* {:username "shortuser" :all? true :extra-joins nil})))
      (is (= {:count 2} (db/search-thought-count *db* {:username "shortuser" :query "archive" :all? true :extra-joins nil})))
      ;; De-archive the first thought, verify
      (memory/archive! to-archive-1)                        ; The original thought should have :archived? false
      (is (= {:count 6} (db/get-thought-count *db* {:username "shortuser" :all? false :extra-joins nil})))
      (is (= {:count 1} (db/search-thought-count *db* {:username "shortuser" :query "archive" :all? false :extra-joins nil})))
      (is (= {:count 7} (db/get-thought-count *db* {:username "shortuser" :all? true :extra-joins nil})))
      (is (= {:count 2} (db/search-thought-count *db* {:username "shortuser" :query "archive" :all? true :extra-joins nil})))
      ))
  )


(deftest test-query-memories
  (tdu/init-placeholder-data!)
  (testing "Querying an empty database returns no values"
    (let [r (memory/query tdu/ph-username)]
      (is (= 0 (:total r)))
      (is (empty? (:results r)))))
  (testing "Query previous value"
    (let [_        (memory/create! {:username tdu/ph-username :thought "Just wondering"})
          result   (memory/query tdu/ph-username)
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
          _        (doseq [m memories] (memory/create! {:username tdu/ph-username :thought m}))
          result   (memory/query tdu/ph-username)]
      (is (= 4 (count (:results result))))
      (is (= 4 (:total result)))
      (let [texts     (extract-text (:results result))
            to-search (conj memories "Just wondering")]
        (doseq [m to-search]
          (is (u/in-seq? texts m))))
      ;; All items are considered open, since we just created them
      (is (= 4 (count (filter #(= :open (:status %)) (:results result)))))))
  (testing "Test querying for a string"
    (let [result (memory/query tdu/ph-username "memory")
          texts  (extract-text (:results result))]
      (is (= 2 (:total result)))
      (is (= 2 (count texts)))
      ;; All items are considered open, since we just created them
      (is (= 2 (count (filter #(= :open (:status %)) (:results result)))))
      ;; All thoughts are created as not archived
      (is (= 2 (count (filter #(false? (:archived? %))
                              (:results result)))))
      ;; Both thoughts contain the word "memory"
      (doseq [m texts]
        (is (re-seq #"memory" m)))))
  (testing "Confirm words from a similar root are returned"
    (let [result (memory/query tdu/ph-username "memories")
          texts  (extract-text (:results result))]
      (is (= 2 (count texts)))
      (is (= 2 (:total result)))
      (doseq [m texts]
        (is (re-seq #"memo" m))
        )))
  (testing "Wonder is considered a root for wondering"
    (let [result (memory/query tdu/ph-username "wonder")
          texts  (extract-text (:results result))]
      (is (= 1 (count texts)))
      (is (re-seq #"wondering" (first texts)))
      ))
  (testing "Matching is OR by default"
    (let [result (memory/query tdu/ph-username "memories second")
          texts  (extract-text (:results result))]
      (is (= 3 (count texts)))
      (is (= 3 (:total result)))
      (doseq [m texts]
        (is (or (re-seq #"memory" m)
                (re-seq #"second" m))))
      ))
  (testing "Verify filtering options for archived thoughts"
    (let [to-archive-1 (memory/create! {:username tdu/ph-username :thought "To archive 1"})
          to-archive-2 (memory/create! {:username tdu/ph-username :thought "To archive 2"})]
      ;; Verify that the thought count includes them both
      (is (= 6 (:total (memory/query tdu/ph-username))))
      (is (= 2 (:total (memory/query tdu/ph-username "archive"))))
      ;; Archive a thought, verify
      (memory/archive! (assoc to-archive-1 :archived? true))
      (is (= 5 (:total (memory/query tdu/ph-username))))
      (is (= 1 (:total (memory/query tdu/ph-username "archive"))))
      (is (empty? (filter #(= to-archive-1 %) (:results (memory/query tdu/ph-username "archive")))))
      (is (= 6 (:total (memory/query tdu/ph-username "" 0 true))))
      (is (= 2 (:total (memory/query tdu/ph-username "archive" 0 true))))
      ;; Archive the second one, verify
      (memory/archive! (assoc to-archive-2 :archived? true))
      (is (= 4 (:total (memory/query tdu/ph-username))))
      (is (= 0 (:total (memory/query tdu/ph-username "archive"))))
      (is (= 6 (:total (memory/query tdu/ph-username "" 0 true))))
      (is (= 2 (:total (memory/query tdu/ph-username "archive" 0 true))))
      ;; De-archive the first thought, verify
      (memory/archive! to-archive-1)                        ; The original thought should have :archived? false
      (is (= 5 (:total (memory/query tdu/ph-username))))
      (is (= 1 (:total (memory/query tdu/ph-username "archive"))))
      (is (empty? (filter #(= to-archive-2 %) (:results (memory/query tdu/ph-username "archive")))))
      (is (= 6 (:total (memory/query tdu/ph-username "" 0 true))))
      (is (= 2 (:total (memory/query tdu/ph-username "archive" 0 true))))
      )))


(deftest test-query-sort-order
  (tdu/init-placeholder-data!)
  (import-placeholder-memories!)
  (testing "Querying without a parameter returns them in inverse date order"
    (let [result (memory/query tdu/ph-username)
          dates  (map :created (:results result))
          ]
      (is (= 22 (:total result)))
      (is (= 3 (:pages result)))
      (is (= dates (reverse (sort dates))))))
  (testing "Querying with a parameter returns them in descending score order"
    (let [result (memory/query tdu/ph-username "memory")
          scores (map :rank (:results result))
          ]
      (is (= 3 (count scores)))
      (is (= 1 (:pages result)))
      (is (= (reverse (sort scores)) scores))
      ))
  (testing "Querying with multiple parameters returns them in descending score order"
    (let [result (memory/query tdu/ph-username "money humor")
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
    (let [result   (memory/query tdu/ph-username)
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
    (let [result   (memory/query tdu/ph-username "" 2)
          thoughts (map :thought (:results result))
          indices  (extract-thought-idx thoughts)]
      (is (= 43 (:total result)))
      (is (= 10 (count thoughts)))
      (is (= 5 (:pages result)))
      ;; Thoughts come in inverse date order by default... meaning
      ;; we'll get them in reverse number order
      (is (= indices (reverse (range 32 42))))))
  (testing "We can pass arbitrary limits"
    (let [result   (memory/query tdu/ph-username "" 2 false :limit 3)
          thoughts (map :thought (:results result))
          indices  (extract-thought-idx thoughts)]
      (is (= 43 (:total result)))
      (is (= 3 (count thoughts)))
      (is (= 15 (:pages result)))
      ;; Thoughts come in inverse date order by default... meaning
      ;; we'll get them in reverse number order
      (is (= indices (reverse (range 39 42))))))
  (testing "Passing a limit higher than the number of elements and no offset returns all"
    (let [result   (memory/query tdu/ph-username "" 0 false :limit 1000)
          thoughts (map :thought (:results result))
          indices  (extract-thought-idx thoughts)]
      ;; Total number of records is higher than the number returned because of offset
      (is (= 43 (:total result)))
      (is (= 43 (count thoughts)))
      (is (= 1 (:pages result)))
      ;; Thoughts come in inverse date order by default... meaning
      ;; we'll get them in reverse number order
      (is (= indices (reverse (range 1 44))))))
  (testing "Passing a limit higher than the number of elements returns all records after the offset"
    (let [result   (memory/query tdu/ph-username "" 2 false :limit 1000)
          thoughts (map :thought (:results result))
          indices  (extract-thought-idx thoughts)]
      ;; Total number of records is higher than the number returned because of offset
      (is (= 43 (:total result)))
      (is (= 41 (count thoughts)))
      (is (= 1 (:pages result)))
      ;; Thoughts come in inverse date order by default... meaning
      ;; we'll get them in reverse number order
      (is (= indices (reverse (range 1 42))))))
  (testing "Querying with too far an offset returns fewer records"
    (let [result   (memory/query tdu/ph-username "" 39)
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
    (let [result   (memory/query tdu/ph-username "dreaming memory money people")
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
    (let [result   (memory/query tdu/ph-username "dreaming memory money people" 2)
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
    (let [result (memory/query tdu/ph-username "dreaming memory money people" 20)
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
    (let [_         (memory/create! {:username tdu/ph-username :thought "Just wondering"})
          m1        (first (:results (memory/query tdu/ph-username "wondering")))
          updated   (memory/update! (assoc m1 :thought "Different text"))
          ;; Ensure that we didn't leave the lexeme table as it was by querying for the
          ;; old search term and the new one
          wondering (first (:results (memory/query tdu/ph-username "wondering")))
          different (first (:results (memory/query tdu/ph-username "different")))
          all       (memory/query tdu/ph-username)]
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
    (let [_       (memory/create! {:username tdu/ph-username :thought "Just wondering"})
          m1      (first (:results (memory/query tdu/ph-username)))
          ;; Force the date as if we created it a while ago
          _       (tdb/update-thought-created! *db* (assoc m1 :created (c/to-date (.minusMillis (t/now) memory/open-duration))))
          updated (memory/update! (assoc m1 :thought "Different text"))
          m2      (first (:results (memory/query tdu/ph-username)))]
      (is (empty? updated))
      (is (= 0 (:total (memory/query tdu/ph-username "text"))))
      (is (= 1 (:total (memory/query tdu/ph-username "wondering"))))
      (is (= :closed (:status m2)))))
  )

(deftest test-can-delete-memory
  (testing "We can delete open thoughts"
    (tdu/init-placeholder-data!)
    (let [_         (memory/create! {:username tdu/ph-username :thought "Just wondering"})
          m1        (first (:results (memory/query tdu/ph-username "wondering")))
          result    (memory/delete! (:id m1))
          after-del (memory/get-by-id (:id m1))
          ;; Ensure that we didn't leave the lexeme table as it was by querying for the
          ;; old search term and the new one
          wondering (first (:results (memory/query tdu/ph-username "wondering")))
          all       (memory/query tdu/ph-username)]
      ;; Pre-update values
      (is m1)
      (is (= "Just wondering" (:thought m1)))
      ;; Check that we did not get anything after removing it
      (is (= 1 result))
      (is (nil? after-del))
      ;; Verify we can't re-delete
      (is (= 0 (memory/delete! (:id m1))))
      ;; Check we updated the lexemes
      (is (nil? wondering))
      (is (= {:total 0 :pages 0 :results []} all))
      ))
  (testing "Cannot delete closed thoughts"
    (tdu/init-placeholder-data!)
    (let [_       (memory/create! {:username tdu/ph-username :thought "Just wondering"})
          m1      (first (:results (memory/query tdu/ph-username)))
          ;; Force the date as if we created it a while ago
          _       (tdb/update-thought-created! *db* (assoc m1 :created (c/to-date (.minusMillis (t/now) memory/open-duration))))
          deleted (memory/delete! (:id m1))
          m2      (memory/get-by-id (:id m1))]
      (is (= 0 deleted))
      (is (= (select-keys m1 [:id :username :thought])
             (select-keys m2 [:id :username :thought])))
      (is (= 0 (:total (memory/query tdu/ph-username "text"))))
      (is (= 1 (:total (memory/query tdu/ph-username "wondering"))))
      (is (= :closed (:status m2)))))
  (testing "A memory's own root_id should be cleared if it has no more children"
    (tdu/init-placeholder-data!)
    (let [_      (memory/create! {:username tdu/ph-username :thought "Just wondering"})
          m1     (first (:results (memory/query tdu/ph-username)))
          _      (memory/create! {:username tdu/ph-username :thought "Second memory" :follow-id (:id m1)})
          m2     (first (:results (memory/query tdu/ph-username)))
          _      (memory/create! {:username tdu/ph-username :thought "Third memory" :follow-id (:id m2)})
          m3     (first (:results (memory/query tdu/ph-username)))
          m1r    (last (:results (memory/query tdu/ph-username))) ; Memories are returned in reverse date order on the default query
          _      (memory/create! {:username tdu/ph-username :thought "Unrelated memory, not for thread"})
          thread (memory/query-thread (:root-id m3))]
      ; Thread includes the updated record for the first memory
      (is (= [m1r m2 m3] thread))
      ;; Deleting a memory does not clear the root_id from any of the other elements on the thread
      (is (= 1 (memory/delete! (:id m3))))
      (is (every? #(= (:id m1) (:root-id %)) (memory/query-thread (:root-id m3))))
      ;; Deleting the last child from a memory thread clears that memory's own root_id
      (is (= 1 (memory/delete! (:id m2))))
      (is (nil? (:root-id (memory/get-by-id (:id m1)))))
      )
    )
  )
