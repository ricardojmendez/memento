(ns memento.test.routes.api
  (:require [clojure.test :refer :all]
            [cognitect.transit :as transit]
            [ring.mock.request :refer [request header body]]
            [memento.handler :refer [app]]
            [memento.db.user :as user]
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

(defn post-request
  "Makes a post request to a URL with a body. Returns a vector with the
  response and the translated body."
  [^String url req-body]
  (let [response (app (-> (request :post url)
                          (body (clj->transit req-body))
                          (header "Content-Type" "application/transit+json; charset=UTF-8")
                          (header "Accept" "application/transit+json, text/plain, */*")))
        data     (transit->clj (:body response))]
    [response data]))


;;;;
;;;; Tests
;;;;


;;;
;;; Authentication
;;;

(deftest test-login
  (tdb/wipe-database!)
  (user/create-user! "user1" "password1")
  (testing "We get a login token when authenticating with a valid username/password"
    (let [[response data] (post-request "/api/auth/login" {:username "user1" :password "password1"})]
      (is (= 201 (:status response)))
      (is (map? data))
      (is (:token data))))
  (testing "We get a 401 when authenticating with an invalid username/password"
    (let [[response data] (post-request "/api/auth/login" {:username "user2" :password "password1"})]
      (is (= 401 (:status response)))
      (is (nil? data)))))


;;;
;;; Memory search and creation
;;;

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


(deftest test-add-memory
  (tdb/init-placeholder-data!)
  (testing "We can add a new memory"
    (let [[response clj-data] (post-request "/api/memory" {:thought "Just a new idea"})]
      (is (= 201 (:status response)))
      (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
      (is (= {:count 1} clj-data))
      ))
  (testing "After adding a memoy, we can query for it"
    (let [[_ clj-data] (get-request "/api/memory")
          item         (first clj-data)]
      (is (seq? clj-data))
      (is (= 1 (count clj-data)))
      (is (= tdb/default-test-user (:username item)))
      (is (= "Just a new idea" (:thought item)))
      (is (:created item))
      (is (:id item)))))


