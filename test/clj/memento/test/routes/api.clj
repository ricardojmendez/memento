(ns memento.test.routes.api
  ;; Let's NOT require memento.auth here, all calls should go through the API
  (:require [clojure.test :refer :all]
            [cognitect.transit :as transit]
            [ring.mock.request :refer [request header body]]
            [memento.handler :refer [app]]
            [memento.db.user :as user]
            [memento.test.db.core :as tdb]
            [memento.test.db.memory :as tdm]
            [memento.test.db.user :as tdu])
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

(defn add-auth-token
  [request auth-token]
  (if (empty? auth-token)
    request
    (header request "Authorization" (str "Token " auth-token))
    ))

(defn get-request
  "Executes a GET request with an optional set of parameters. Returns
  a vector with the response and the translated body"
  ([url]
   (get-request url nil nil))
  ([url params auth-token]
   (let [response (app (-> (request :get url params)
                           (header "Accept" "application/transit+json")
                           (add-auth-token auth-token)))]
     [response (transit->clj (:body response))])))


(defn post-request
  "Makes a post request to a URL with a body. Returns a vector with the
  response and the translated body."
  [^String url req-body auth-token]
  (let [response (app (-> (request :post url)
                          (body (clj->transit req-body))
                          (header "Content-Type" "application/transit+json; charset=UTF-8")
                          (header "Accept" "application/transit+json, text/plain, */*")
                          (add-auth-token auth-token)))
        data     (transit->clj (:body response))]
    [response data]))


(defn invoke-login
  "Invokes the login function and returns a token"
  [items]
  (let [[_ data] (post-request "/api/auth/login" items nil)]
    (:token data)
    ))


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
    (let [[response data] (post-request "/api/auth/login" {:username "user1" :password "password1"} nil)]
      (is (= 201 (:status response)))
      (is (map? data))
      (is (:token data))))
  (testing "We get a 401 when authenticating with an invalid username/password"
    (let [[response data] (post-request "/api/auth/login" {:username "user2" :password "password1"} nil)]
      (is (= 401 (:status response)))
      (is (nil? data)))))


;;;
;;; Memory search and creation
;;;

(deftest test-search-memory
  (tdu/init-placeholder-data!)
  (tdm/import-placeholder-memories!)
  (let [token (invoke-login {:username tdu/ph-username :password tdu/ph-password})]
    (testing "Search request should not include a trailing slash"
      (let [[response _] (get-request "/api/memory/search/?q=")]
        (is response)
        (is (= 404 (:status response)))))
    (testing "GETting just 'memory' returns all thoughts"
      (let [[response clj-data] (get-request "/api/memory" nil token)]
        (is response)
        (is (= 200 (:status response)))
        (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
        (is (= 22 (count clj-data)))
        (doseq [e clj-data]
          (is (= tdu/ph-username (:username e)))
          (is (= String (type (:created e)))))
        ))
    (testing "Searching without a query returns all elements"
      (let [[response clj-data] (get-request "/api/memory/search" nil token)]
        (is response)
        (is (= 200 (:status response)))
        (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
        (is (= 22 (count clj-data)))
        (doseq [e clj-data]
          (is (= tdu/ph-username (:username e)))
          (is (= String (type (:created e)))))
        ))
    (testing "Searching with a query filters the items"
      (let [[response clj-data] (get-request "/api/memory/search?q=always" nil token)]
        (is response)
        (is (= 200 (:status response)))
        (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
        (is (= 2 (count clj-data)))
        (doseq [e clj-data]
          (is (= tdu/ph-username (:username e)))
          (is (re-seq #"always" (:thought e))))))
    (testing "Passing multiple values uses them as OR"
      ;; The following could also have been passed as "?q=always+money"
      (let [[response clj-data] (get-request "/api/memory/search" {:q "always money"} token)]
        (is response)
        (is (= 200 (:status response)))
        (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
        (is (= 5 (count clj-data)))
        (doseq [e clj-data]
          (is (= tdu/ph-username (:username e)))
          (is (or (re-seq #"always" (:thought e))
                  (re-seq #"money" (:thought e))
                  )))))
    )
  ;; Create a new user and confirm we only get his memories when querying
  (user/create-user! "user1" "ssh!")
  (let [token (invoke-login {:username "user1" :password "ssh!"})]
    ;; Add a memory
    (post-request "/api/memory" {:thought "user1 - No thoughts in common with the previous ideas"} token)
    (post-request "/api/memory" {:thought "user1 - No siree"} token)
    ;; On to testing
    (testing "GETting just 'memory' returns only thoughts for this user"
      (let [[response clj-data] (get-request "/api/memory" nil token)]
        (is response)
        (is (= 200 (:status response)))
        (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
        (is (= 2 (count clj-data)))
        (doseq [e clj-data]
          (is (= "user1" (:username e)))
          (is (= String (type (:created e))))
          (is (re-seq #"user1" (:thought e))))
        ))
    (testing "Querying for 'always money' returns no values, even though we know there are records in the database"
      (let [[response clj-data] (get-request "/api/memory/search" {:q "always money"} token)]
        (is response)
        (is (= 200 (:status response)))
        (is (= 0 (count clj-data))))))
  ;; Ensure our default user is also isolated from the new thoughts
  (let [token (invoke-login {:username tdu/ph-username :password tdu/ph-password})
        [_ clj-data] (get-request "/api/memory" nil token)]
    (is (= 22 (count clj-data)))
    (is (every? #(= tdu/ph-username (:username %)) clj-data)))
  )


(deftest test-add-memory
  (tdu/init-placeholder-data!)
  (user/create-user! "user1" "password1")
  (let [token (invoke-login {:username "user1" :password "password1"})]
    (testing "Attempting to add a memory without a token results in a 401"
      (testing "We can add a new memory"
        (let [[response _] (post-request "/api/memory" {:thought "Just a new idea"} nil)]
          (is (= 401 (:status response)))
          )))
    (testing "We can add a new memory"
      (let [[response clj-data] (post-request "/api/memory" {:thought "Just a new idea"} token)]
        (is (= 201 (:status response)))
        (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
        (is (= {:count 1} clj-data))
        ))
    (testing "After adding a memoy, we can query for it"
      (let [[_ clj-data] (get-request "/api/memory" nil token)
            item (first clj-data)]
        (is (seq? clj-data))
        (is (= 1 (count clj-data)))
        (is (= "user1" (:username item)))
        (is (= "Just a new idea" (:thought item)))
        (is (:created item))
        (is (:id item))))
    ))


