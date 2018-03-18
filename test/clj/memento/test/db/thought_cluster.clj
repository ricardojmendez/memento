(ns memento.test.db.thought-cluster
  (:require [clojure.string :refer [split-lines split]]
            [clojure.set :refer [intersection]]
            [clojure.test :refer :all]
            [memento.db.core :refer [*db*] :as db]
            [memento.db.memory :as memory]
            [memento.db.thought-cluster :as tc]
            [memento.db.user :as user]
            [memento.test.db.user :as tdu]
            [mount.core :as mount]
            [memento.test.db.memory :as tdm])
  (:import [java.util UUID]))

(use-fixtures
  :once
  (fn [f]
    (mount/start
      #'memento.config/env
      #'memento.db.core/*db*)
    (f)))


;;;
;;; Raw base functions functions
;;;

(deftest test-create-cluster
  (tdu/init-placeholder-data!)
  (let [cluster (db/create-cluster! {:username tdu/ph-username})]
    (is (:id cluster))
    (is (:created cluster))
    (is (= tdu/ph-username (:username cluster)))))


(deftest test-add-thoughts-to-cluster
  (tdu/init-placeholder-data!)
  (tdm/import-placeholder-memories!)
  (let [cluster    (db/create-cluster! {:username tdu/ph-username})
        query      (memory/query tdu/ph-username)
        thoughts   (:results query)
        add-result (tc/add-thoughts (:id cluster) (map :id thoughts))]
    (is cluster)
    (is (= 10 (count thoughts)))
    (is (= 10 add-result))))


(deftest test-get-cluster-thoughts
  (tdu/init-placeholder-data!)
  (tdm/import-placeholder-memories!)
  (testing "We can retrieve a cluster "
    (let [cluster    (db/create-cluster! {:username tdu/ph-username})
          query      (memory/query tdu/ph-username)
          thoughts   (take 4 (:results query))
          _          (tc/add-thoughts (:id cluster) (map :id thoughts))
          get-result (tc/get-thoughts tdu/ph-username (:id cluster))]
      (is cluster)
      (is (map? get-result))
      (is (= 4 (count (:results get-result))))
      (is (= (set (map :id thoughts))
             (set (map :id (:results get-result)))))))
  (testing "Result is empty if the owner does not match"
    (let [cluster    (db/create-cluster! {:username tdu/ph-username})
          query      (memory/query tdu/ph-username)
          thoughts   (take 5 (:results query))
          _          (tc/add-thoughts (:id cluster) (map :id thoughts))
          get-result (tc/get-thoughts "other-id" (:id cluster))]
      (is cluster)
      (is (= tdm/empty-query
             get-result))))
  (testing "Result is empty if the cluster does not exist"
    (is (= tdm/empty-query
           (tc/get-thoughts "other-id" nil)
           (tc/get-thoughts "other-id" (UUID/randomUUID))
           (tc/get-thoughts tdu/ph-username (UUID/randomUUID))))))


;;;
;;; Higher level wrappers only
;;;

(deftest test-cluster-thoughts
  (tdu/init-placeholder-data!)
  (tdm/import-placeholder-memories!)
  (let [query      (memory/query tdu/ph-username)
        thoughts   (take 6 (:results query))
        cluster    (tc/cluster-thoughts tdu/ph-username (map :id thoughts))
        get-result (tc/get-thoughts tdu/ph-username (:id cluster))]
    (is cluster)
    (is (= 6 (count (:results get-result))))
    (is (= (set (map :id thoughts))
           (set (map :id (:results get-result)))))))

(deftest test-get-clusters
  (tdu/init-placeholder-data!)
  (tdm/import-placeholder-memories!)
  (testing "Retrieve a known list of clusters"
    (let [query     (memory/query tdu/ph-username)
          group-1   (take 6 (:results query))
          group-2   (take 5 (drop 3 (:results query)))
          cluster-1 (tc/cluster-thoughts tdu/ph-username (map :id group-1))
          cluster-2 (tc/cluster-thoughts tdu/ph-username (map :id group-2))
          clusters  (tc/get-clusters tdu/ph-username)]
      (is (= [cluster-2 cluster-1] clusters))))
  (testing "A user only gets his clusters"
    (user/create! "shortuser" "somepass")
    (tdm/import-placeholder-memories! "shortuser" "quotes2.txt")
    (let [query     (memory/query "shortuser")
          group-2   (take 3 (drop 2 (:results query)))
          cluster-2 (tc/cluster-thoughts "shortuser" (map :id group-2))
          clusters  (tc/get-clusters "shortuser")]
      (is (= [cluster-2] clusters))))
  (testing "Invalid username returns nil"
    (is (nil? (tc/get-clusters "invalid")))))


(deftest test-filter-ownership-on-creation
  (tdu/init-placeholder-data!)
  (tdm/import-placeholder-memories!)
  (user/create! "shortuser" "somepass")
  (testing "User cannot create a cluster for thoughts that don't belong to him"
    (let [query      (memory/query tdu/ph-username)
          thoughts   (take 6 (:results query))
          cluster    (tc/cluster-thoughts "shortuser" (map :id thoughts))
          get-result (tc/get-thoughts tdu/ph-username (:id cluster))]
      (is (nil? cluster))
      (is (= 0 (count (:results get-result))))))
  (testing "Thoughts that do not belong to the user are filtered out"
    (let [query      (memory/query tdu/ph-username)
          thoughts   (take 4 (:results query))
          other      (memory/create! {:username "shortuser" :thought "Just wondering"})
          own-ids    (map :id thoughts)
          all-ids    (conj own-ids (:id other))
          cluster    (tc/cluster-thoughts tdu/ph-username all-ids)
          get-result (tc/get-thoughts tdu/ph-username (:id cluster))]
      (is cluster)
      (is (= (set own-ids)
             (set (map :id (:results get-result))))))))


(deftest test-remove-thought
  (tdu/init-placeholder-data!)
  (tdm/import-placeholder-memories!)
  (testing "We can remove a thought from a cluster"
    (let [query     (memory/query tdu/ph-username)
          group-1   (take 6 (:results query))
          cluster-1 (tc/cluster-thoughts tdu/ph-username (map :id group-1))
          to-delete (:id (second group-1))
          result    (tc/remove-thought tdu/ph-username (:id cluster-1) to-delete)
          after-del (tc/get-thoughts tdu/ph-username (:id cluster-1))]
      (is (= 1 result))
      (is (= 5 (count (:results after-del))))
      ;; The next two are equivalent, but when running on the REPL, Cursive
      ;; evaluates the first one properly as a test when running it on the REPL,
      ;; but for the second one it outputs instead of running a test.
      ;;
      ;; See https://gitlab.com/Numergent/memento/issues/112#note_63788223
      #_(is (empty? (filter #(= (:id to-delete) (:id %)) (:results after-del))))
      (is (not-any? #(= (:id to-delete) (:id %)) (:results after-del)))))
  (testing "If we remove all thoughts from a cluster, it ends up empty"
    (let [query           (memory/query tdu/ph-username)
          group-1         (take 6 (:results query))
          cluster-1       (tc/cluster-thoughts tdu/ph-username (map :id group-1))
          clusters-before (tc/get-clusters tdu/ph-username)
          ;; Remove all thoughts
          _               (doseq [thought group-1]
                            (tc/remove-thought tdu/ph-username (:id cluster-1) (:id thought)))
          clusters-after  (tc/get-clusters tdu/ph-username)
          thoughts-after  (tc/get-thoughts tdu/ph-username (:id cluster-1))]
      (is (= tdm/empty-query thoughts-after))
      (is (= 2 (count clusters-before)))
      (is (= 1 (count clusters-after)))
      (is (not= [cluster-1] clusters-after))))
  (testing "The thoughts aren't removed if the username does not match"
    (let [clusters-before (tc/get-clusters tdu/ph-username)
          cluster         (first clusters-before)
          thoughts-before (tc/get-thoughts tdu/ph-username (:id cluster))
          ;; Remove all thoughts
          _               (doseq [thought (:results thoughts-before)]
                            (tc/remove-thought "someone-else" (:id cluster) (:id thought)))
          clusters-after  (tc/get-clusters tdu/ph-username)
          thoughts-after  (tc/get-thoughts tdu/ph-username (:id cluster))]
      (is (= thoughts-before thoughts-after))
      (is (= clusters-before clusters-after)))))