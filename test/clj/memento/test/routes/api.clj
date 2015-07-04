(ns memento.test.routes.api
  (:require [clojure.test :refer :all]
            [cognitect.transit :as transit]
            [ring.mock.request :refer [request header body]]
            [memento.handler :refer [app]]
            [memento.test.db.core :as tdb]
            [memento.test.db.memory :as tdm])
  (:import java.io.ByteArrayOutputStream))


;;;;
;;;; Helpers
;;;;

(defn transit->clj
  "Receives a byte array expected to contain transit+json, and coverts it
  to a clojure data structure"
  [arr]
  (try
    (let [reader (transit/reader arr :json)]
      (transit/read reader))
    (catch Exception _ nil)))

(defn clj->transit
  [data]
  (let [out    (ByteArrayOutputStream. 4096)
        writer (transit/writer out :json)]
    (transit/write writer data)
    (.toString out)))

(defn get-request
  "Executes a GET request with an optional set of parameters. Returns
  a vector with the response and the translated body"
  ([url]
   (get-request url nil))
  ([url params]
   (let [response (app (-> (request :get url params)
                           (header "Accept" "application/transit+json")))]
     [response (transit->clj (:body response))])))

;;;;
;;;; Tests
;;;;


(deftest test-search-memory
  (tdb/init-placeholder-data!)
  (tdm/import-placeholder-memories!)
  (testing "Search request should not include a trailing slash"
    (let [[response _] (get-request "/api/memory/search/?q=")]
      (is response)
      (is (= 404 (:status response)))))
  (testing "GETting just 'memory' returns all thoughts"
    (let [[response clj-data] (get-request "/api/memory" nil)]
      (is response)
      (is (= 200 (:status response)))
      (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
      (is (= 22 (count clj-data)))
      (doseq [e clj-data]
        (is (= tdb/default-test-user (:username e)))
        (is (= String (type (:created e)))))
      ))
  (testing "Searching without a query returns all elements"
    (let [[response clj-data] (get-request "/api/memory/search")]
      (is response)
      (is (= 200 (:status response)))
      (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
      (is (= 22 (count clj-data)))
      (doseq [e clj-data]
        (is (= tdb/default-test-user (:username e)))
        (is (= String (type (:created e)))))
      ))
  (testing "Searching with a query filters the items"
    (let [[response clj-data] (get-request "/api/memory/search?q=always")]
      (is response)
      (is (= 200 (:status response)))
      (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
      (is (= 2 (count clj-data)))
      (doseq [e clj-data]
        (is (= tdb/default-test-user (:username e)))
        (is (re-seq #"always" (:thought e))))))
  (testing "Passing multiple values uses them as OR"
    ;; The following could also have been passed as "?q=always+money"
    (let [[response clj-data] (get-request "/api/memory/search" {:q "always money"})]
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
