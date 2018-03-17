(ns memento.test.routes.api.thought-cluster
  (:require [clojure.test :refer :all]
            [clj-time.coerce :as c]
            [clj-time.core :as t]
            [memento.handler :refer [app]]
            [memento.config :refer [env]]
            [memento.db.user :as user]
            [memento.test.db.core :as tdb]
            [memento.test.db.memory :as tdm]
            [memento.test.db.user :as tdu]
            [memento.test.routes.helpers :refer [post-request patch-request get-request put-request del-request invoke-login]]
            [memento.db.core :refer [*db*]]
            [memento.db.memory :as memory]
            [mount.core :as mount])
  (:import (java.util UUID)))

(use-fixtures
  :once
  (fn [f]
    (mount/start
      #'memento.config/env
      #'memento.db.core/*db*)
    (f)))

(def empty-result {:total 0, :pages 0, :current-page 0, :results []})

;;;;
;;;; Tests
;;;;


;;;
;;; Adding a cluster
;;;


(deftest test-create-cluster
  (tdu/init-placeholder-data!)
  (tdm/import-placeholder-memories!)
  (let [query (memory/query tdu/ph-username)
        token (invoke-login {:username tdu/ph-username :password tdu/ph-password})]
    (testing "Attempting to add a cluster without a token results in a 400"
      (let [[response _] (post-request "/api/clusters" {} nil)]
        (is (= 400 (:status response)))))
    (testing "We cannot add empty clusters"
      (let [[response _] (post-request "/api/clusters" [] token)]
        (is (= 400 (:status response)))))
    (testing "We can create a new cluster"
      (let [thoughts (map :id (take 6 (:results query)))
            [response record] (post-request "/api/clusters" {:thought-ids thoughts} token)]
        (is (= 201 (:status response)))
        (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
        (is (map? record))
        (is (:id record))
        (is (= (str "http://localhost/api/clusters/" (:id record)) (get-in response [:headers "Location"])))))))


(deftest test-cluster-retrieve
  (tdu/init-placeholder-data!)
  (tdm/import-placeholder-memories!)
  (let [query (memory/query tdu/ph-username)
        token (invoke-login {:username tdu/ph-username :password tdu/ph-password})]
    (testing "After adding a cluster, we can retrieve it"
      (let [thoughts    (take 6 (:results query))
            thought-ids (map :id thoughts)
            [_ record] (post-request "/api/clusters" {:thought-ids thought-ids} token)
            [response result] (get-request (str "/api/clusters/" (:id record)) nil token)]
        (is (= 200 (:status response)))
        (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
        (is (map? record))
        (is (:id record))
        (is (= (set thoughts)
               (set (:results result))))))
    (testing "Attempting to retrieve a cluster with an invalid token fails"
      (let [thoughts    (take 6 (:results query))
            thought-ids (map :id thoughts)
            [_ record] (post-request "/api/clusters" {:thought-ids thought-ids} token)
            [response result] (get-request (str "/api/clusters/" (:id record)) nil "invalid")]
        (is (= 401 (:status response)))
        (is (= {:error "unauthorized"} result))))
    (testing "Attempting to retrieve a cluster with different user's token returns empty"
      (user/create! "user1" "password1")
      (let [thoughts    (take 6 (:results query))
            thought-ids (map :id thoughts)
            [_ record] (post-request "/api/clusters" {:thought-ids thought-ids} token)
            other-token (invoke-login {:username "User1" :password "password1"})
            [response result] (get-request (str "/api/clusters/" (:id record)) nil other-token)]
        (is (= 200 (:status response)))
        (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
        (is (= empty-result result))))
    (testing "Attempting to retrieve a non-existent cluster returns empty"
      (let [[response result] (get-request (str "/api/clusters/" (UUID/randomUUID)) nil token)]
        (is (= 200 (:status response)))
        (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
        (is (= empty-result result))))))


(deftest test-get-cluster-list
  (tdu/init-placeholder-data!)
  (tdm/import-placeholder-memories!)
  (let [query (memory/query tdu/ph-username)
        token (invoke-login {:username tdu/ph-username :password tdu/ph-password})
        [_ c1] (post-request "/api/clusters" {:thought-ids (map :id (take 6 (:results query)))} token)
        [r1 rc1] (get-request "/api/clusters" nil token)
        [_ c2] (post-request "/api/clusters" {:thought-ids (map :id (take 2 (drop 3 (:results query))))} token)
        [_ rc2] (get-request "/api/clusters" nil token)]
    (testing "After creating a cluster, we can retrieve it"
      (is (= 200 (:status r1)))
      (is (= "application/transit+json" (get-in r1 [:headers "Content-Type"])))
      (is (= [c1] rc1)))
    (testing "Clusters are returned in inverse create order"
      (is (= [c2 c1] rc2)))
    (testing "Attempting to retrieve with an invalid token fails authorization"
      (let [[response result] (get-request "/api/clusters" nil "invalid")]
        (is (= 401 (:status response)))
        (is (= {:error "unauthorized"} result))))
    (testing "Attempting to retrieve the clusters with different user's token returns empty"
      (user/create! "user1" "password1")
      (let [other-token (invoke-login {:username "User1" :password "password1"})
            [response result] (get-request "/api/clusters" nil other-token)]
        (is (= 200 (:status response)))
        (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
        (is (empty? result))))
    (testing "Users only view their own clusters"
      (tdm/import-placeholder-memories! "user1")
      (let [user1-memories (memory/query "user1")
            other-token (invoke-login {:username "User1" :password "password1"})
            [_ oc1] (post-request "/api/clusters" {:thought-ids (map :id (take 5 (drop 3 (:results user1-memories))))} other-token)
            [_ clusters-1] (get-request "/api/clusters" nil token)
            [_ clusters-2] (get-request "/api/clusters" nil other-token)]
        (is (= [c2 c1] clusters-1))
        (is (= [oc1] clusters-2)))
      )))