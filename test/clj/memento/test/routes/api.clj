(ns memento.test.routes.api
  (:require [clojure.test :refer :all]
            [cognitect.transit :as transit]
            [ring.mock.request :refer [request header]]
            [memento.handler :refer [app]]
            [memento.test.db.core :as tdb]
            [memento.test.db.memory :as tdm]))


;;;;
;;;; Helpers
;;;;

(defn transit->clj
  [arr]
  (let [in     arr
        reader (transit/reader in :json)]
    (transit/read reader)))

;;;;
;;;; Tests
;;;;


(deftest test-search-memory
  (tdb/init-placeholder-data!)
  (tdm/import-placeholder-memories!)
  (testing "Search request should not include a trailing slash"
    (let [response (app (request :get "/api/memory/search/?q="))]
      (is response)
      (is (= 404 (:status response)))))
  (testing "GETting just 'memory' returns all thoughts"
    (let [response (app (-> (request :get "/api/memory")
                            (header "Accept" "application/transit+json")))
          clj-data (transit->clj (:body response))]
      (is response)
      (is (= 200 (:status response)))
      (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
      (is (= 22 (count clj-data)))
      (doseq [e clj-data]
        (is (= tdb/default-test-user (:username e)))
        (is (= String (type (:created e)))))
      ))
  (testing "Searching without a query returns all elements"
    (let [response (app (-> (request :get "/api/memory/search")
                            (header "Accept" "application/transit+json")))
          clj-data (transit->clj (:body response))]
      (is response)
      (is (= 200 (:status response)))
      (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
      (is (= 22 (count clj-data)))
      (doseq [e clj-data]
        (is (= tdb/default-test-user (:username e)))
        (is (= String (type (:created e)))))
      ))
  (testing "Searching with a query filters the items"
    (let [response (app (-> (request :get "/api/memory/search?q=always")
                            (header "Accept" "application/transit+json")))
          clj-data (transit->clj (:body response))]
      (is response)
      (is (= 200 (:status response)))
      (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
      (is (= 2 (count clj-data)))
      (doseq [e clj-data]
        (is (= tdb/default-test-user (:username e)))
        (is (re-seq #"always" (:thought e))))))
  (testing "Passing multiple values uses them as OR"
    ;; The following could also have been passed as "?q=always+money"
    (let [response (app (-> (request :get "/api/memory/search" {:q "always money"})
                            (header "Accept" "application/transit+json")))
          clj-data (transit->clj (:body response))]
      (is response)
      (is (= 200 (:status response)))
      (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
      (is (= 5 (count clj-data)))
      (doseq [e clj-data]
        (is (= tdb/default-test-user (:username e)))
        (is (or (re-seq #"always" (:thought e))
                (re-seq #"money" (:thought e))
                )))))
  )
