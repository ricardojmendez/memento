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
            [memento.test.db.memory :as tdm]))

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
          get-result (tc/get-thoughts (:id cluster) tdu/ph-username)]
      (is cluster)
      (is (= 4 (count get-result)))
      (is (= (set (map :id thoughts))
             (set (map :thought-id get-result))))))
  (testing "Cluster is nil if the owner does not match"
    (let [cluster    (db/create-cluster! {:username tdu/ph-username})
          query      (memory/query tdu/ph-username)
          thoughts   (take 5 (:results query))
          _          (tc/add-thoughts (:id cluster) (map :id thoughts))
          get-result (tc/get-thoughts (:id cluster) "other-id")]
      (is cluster)
      (is (nil? get-result)))))


;;;
;;; Higher level wrappers only
;;;

(deftest test-cluster-thoughts
  (tdu/init-placeholder-data!)
  (tdm/import-placeholder-memories!)
  (let [query      (memory/query tdu/ph-username)
        thoughts   (take 6 (:results query))
        cluster    (tc/cluster-thoughts tdu/ph-username (map :id thoughts))
        get-result (tc/get-thoughts (:id cluster) tdu/ph-username)]
    (is cluster)
    (is (= 6 (count get-result)))
    (is (= (set (map :id thoughts))
           (set (map :thought-id get-result))))))

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
          get-result (tc/get-thoughts (:id cluster) tdu/ph-username)]
      (is (nil? cluster))
      (is (= 0 (count get-result)))))
  (testing "Thoughts that do not belong to the user are filtered out"
    (let [query      (memory/query tdu/ph-username)
          thoughts   (take 4 (:results query))
          other      (memory/create! {:username "shortuser" :thought "Just wondering"})
          own-ids    (map :id thoughts)
          all-ids    (conj own-ids (:id other))
          cluster    (tc/cluster-thoughts tdu/ph-username all-ids)
          get-result (tc/get-thoughts (:id cluster) tdu/ph-username)]
      (is cluster)
      (is (= 4 (count get-result)))
      (is (= (set own-ids)
             (set (map :thought-id get-result)))))))