(ns memento.test.routes.api
  ;; Let's NOT require memento.auth here, all calls should go through the API
  (:require [clojure.test :refer :all]
            [clj-time.coerce :as c]
            [clj-time.core :as t]
            [cognitect.transit :as transit]
            [memento.handler :refer [app]]
            [memento.db.user :as user]
            [memento.test.db.core :as tdb]
            [memento.test.db.memory :as tdm]
            [memento.test.db.user :as tdu]
            [memento.db.core :refer [*db*] :as db]
            [memento.db.memory :as memory]
            [ring.mock.request :refer [request header body]]
            [clojure.string :as string])
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
    (header request "Authorization" (str "Token " auth-token))))

(defn get-request
  "Executes a GET request with an optional set of parameters. Returns
  a vector with the response and the translated body"
  ([url]
   (get-request url nil nil))
  ([url params auth-token]
   (let [response ((app) (-> (request :get url params)
                             (header "Accept" "application/transit+json")
                             (add-auth-token auth-token)))]
     [response (transit->clj (:body response))])))

(defn req-with-body
  [req-type ^String url id path req-body auth-token]
  (let [response ((app) (-> (request req-type
                                     (->
                                       [url
                                        (when id ["/" id])
                                        (when path ["/" path])]
                                       flatten
                                       string/join))
                            (body (clj->transit req-body))
                            (header "Content-Type" "application/transit+json; charset=UTF-8")
                            (header "Accept" "application/transit+json, text/plain, */*")
                            (add-auth-token auth-token)))
        data     (transit->clj (:body response))]
    [response data]))


(defn post-request
  "Makes a post request to a URL with a body. Returns a vector with the
  response and the translated body."
  [^String url req-body auth-token]
  (req-with-body :post url nil nil req-body auth-token))

(defn put-request
  "Makes a put request to a URL with a body, under a specific path. Returns
  a vector with the response and the translated body."
  [^String url id req-body auth-token]
  (req-with-body :put url id nil req-body auth-token))

(defn del-request
  "Makes a delete request to a URL with a body, under a specific path. Returns
  a vector with the response and the translated body."
  [^String url id auth-token]
  (req-with-body :delete url id nil nil auth-token))


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
  (tdb/wipe-database! *db*)
  (user/create-user! "user1" "password1")
  (testing "We get a login token when authenticating with a valid username/password"
    (let [[response data] (post-request "/api/auth/login" {:username "user1" :password "password1"} nil)]
      (is (= 201 (:status response)))
      (is (map? data))
      (is (:token data))))
  (testing "Auth is not case-sensitive on the username"
    (let [[response data] (post-request "/api/auth/login" {:username "User1" :password "password1"} nil)]
      (is (= 201 (:status response)))
      (is (map? data))
      (is (:token data))))
  (testing "We get a 401 when authenticating with an invalid username/password"
    (let [[response data] (post-request "/api/auth/login" {:username "user2" :password "password1"} nil)]
      (is (= 401 (:status response)))
      (is (nil? data))))
  (testing "Auth is case-sensitive on the password"
    (let [[response data] (post-request "/api/auth/login" {:username "user1" :password "Password1"} nil)]
      (is (= 401 (:status response)))
      (is (nil? data))))
  )


(deftest test-signup
  (tdb/wipe-database! *db*)
  (let [username "newuser"
        password "password"]
    (testing "Attempting to log in with the credentials initially results on a 401"
      (let [[response data] (post-request "/api/auth/login" {:username username :password password} nil)]
        (is (= 401 (:status response)))
        (is (nil? data))))
    (testing "We get a login token when signing up with a valid username/password"
      (let [[response data] (post-request "/api/auth/signup" {:username username :password password} nil)]
        (is (= 201 (:status response)))
        (is (map? data))
        (is (:token data))))
    (testing "Attempting to log in with the credentials after creating it results on a token"
      (let [[response data] (post-request "/api/auth/login" {:username username :password password} nil)]
        (is (= 201 (:status response)))
        (is (:token data))))
    (testing "Attempting to sign up with the same username/password results on an error"
      (let [[response data] (post-request "/api/auth/signup" {:username username :password password} nil)]
        (is (= 409 (:status response)))
        (is (:error data))))
    (testing "Attempting to sign up with the same username results on an error"
      (let [[response data] (post-request "/api/auth/signup" {:username username :password "password2"} nil)]
        (is (= 409 (:status response)))
        (is (:error data))))
    (testing "Attempting to sign up with empty username fails"
      (let [[response data] (post-request "/api/auth/signup" {:username "" :password password} nil)]
        (is (= 409 (:status response)))
        (is (:error data))))
    (testing "Attempting to sign up with empty password fails"
      (let [[response data] (post-request "/api/auth/signup" {:username username :password ""} nil)]
        (is (= 409 (:status response)))
        (is (:error data))))
    (testing "We get a login token when signing up with a new username/password"
      (let [[response data] (post-request "/api/auth/signup" {:username "u1" :password "p1"} nil)]
        (is (= 201 (:status response)))
        (is (map? data))
        (is (:token data))))
    ))


;;;
;;; Memory search and creation
;;;

(deftest test-search-memory
  (tdu/init-placeholder-data!)
  (tdm/import-placeholder-memories!)
  (let [token (invoke-login {:username tdu/ph-username :password tdu/ph-password})]
    (testing "Search request should not include a trailing slash"
      (let [[response _] (get-request "/api/search/?q=")]
        (is response)
        (is (= 404 (:status response)))))
    (testing "GETting just 'memory' returns all thoughts"
      (let [[response clj-data] (get-request "/api/thoughts" nil token)
            {:keys [total results]} clj-data]
        (is response)
        (is (= 200 (:status response)))
        (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
        (is (= 10 (count results)))
        (is (= 22 total))
        (doseq [e results]
          (is (= tdu/ph-username (:username e)))
          (is (= String (type (:created e)))))
        ))
    (testing "Searching without a query returns all elements"
      (let [[response clj-data] (get-request "/api/search" nil token)
            {:keys [total results]} clj-data]
        (is response)
        (is (= 200 (:status response)))
        (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
        (is (= 10 (count results)))
        (is (= 22 total))
        (doseq [e results]
          (is (= tdu/ph-username (:username e)))
          (is (= String (type (:created e)))))
        ))
    (testing "Searching with a query filters the items"
      (let [[response clj-data] (get-request "/api/search?q=always" nil token)
            {:keys [total results]} clj-data]
        (is response)
        (is (= 200 (:status response)))
        (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
        (is (= 2 (count results)))
        (is (= 2 total))
        (doseq [e results]
          (is (= tdu/ph-username (:username e)))
          (is (re-seq #"always" (:thought e))))))
    (testing "We can send trailing or leading spaces and the query is trimmed"
      (let [[response clj-data] (get-request "/api/search" {:q " always  "} token)
            {:keys [total results]} clj-data]
        (is response)
        (is (= 200 (:status response)))
        (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
        (is (= 2 (count results)))
        (is (= 2 total))
        (doseq [e results]
          (is (= tdu/ph-username (:username e)))
          (is (re-seq #"always" (:thought e))))
        ))
    (testing "Invalid symbols are trimmed"
      (let [[response clj-data] (get-request "/api/search" {:q ";always&+-|!!!$."} token)
            {:keys [total results]} clj-data]
        (is response)
        (is (= 200 (:status response)))
        (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
        (is (= 2 (count results)))
        (is (= 2 total))
        (doseq [e results]
          (is (= tdu/ph-username (:username e)))
          (is (re-seq #"always" (:thought e))))
        ))
    (testing "Sending a blank query is treated the same as no query"
      (let [[r1 d1] (get-request "/api/search" nil token)
            [r2 d2] (get-request "/api/search" {:q " "} token)]
        (is (= 200 (:status r1) (:status r2)))
        (is (= d1 d2))
        ))
    (testing "Passing multiple values uses them as OR"
      ;; The following could also have been passed as "?q=always+money"
      (let [[response clj-data] (get-request "/api/search" {:q "always money"} token)
            {:keys [total results]} clj-data]
        (is response)
        (is (= 200 (:status response)))
        (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
        (is (= 5 (count results)))
        (is (= 5 total))
        (doseq [e results]
          (is (= tdu/ph-username (:username e)))
          (is (or (re-seq #"always" (:thought e))
                  (re-seq #"money" (:thought e))
                  )))))
    (testing "Multiple spaces are consolidated"
      (let [[response clj-data] (get-request "/api/search" {:q "always   money "} token)
            {:keys [_ results]} clj-data]
        (is response)
        (is (= 200 (:status response)))
        (is (= 5 (count results)))
        (doseq [e results]
          (is (or (re-seq #"always" (:thought e))
                  (re-seq #"money" (:thought e))
                  )))))
    )
  ;; Create a new user and confirm we only get his memories when querying
  (user/create-user! "user1" "ssh!")
  (let [token (invoke-login {:username "user1" :password "ssh!"})]
    ;; Add a memory
    (post-request "/api/thoughts" {:thought "user1 - No thoughts in common with the previous ideas"} token)
    (post-request "/api/thoughts" {:thought "user1 - No siree"} token)
    ;; On to testing
    (testing "GETting just 'memory' returns only thoughts for this user"
      (let [[response clj-data] (get-request "/api/thoughts" nil token)
            {:keys [total results]} clj-data]
        (is response)
        (is (= 200 (:status response)))
        (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
        (is (= 2 total))
        (doseq [e results]
          (is (= "user1" (:username e)))
          (is (= String (type (:created e))))
          (is (re-seq #"user1" (:thought e))))
        ))
    (testing "Querying for 'always money' returns no values, even though we know there are records in the database"
      (let [[response clj-data] (get-request "/api/search" {:q "always money"} token)]
        (is response)
        (is (= 200 (:status response)))
        (is (= {:total 0 :results '() :current-page 0 :pages 0} clj-data)))))
  ;; Ensure our default user is also isolated from the new thoughts
  (let [token (invoke-login {:username tdu/ph-username :password tdu/ph-password})
        [_ {:keys [total results]}] (get-request "/api/thoughts" nil token)]
    (is (= 22 total))
    (is (= 10 (count results)))
    (is (every? #(= tdu/ph-username (:username %)) results)))
  )


(deftest test-search-memory-paged
  (tdu/init-placeholder-data!)
  (tdm/import-placeholder-memories! tdu/ph-username "numbers.txt")
  (let [token (invoke-login {:username tdu/ph-username :password tdu/ph-password})]
    (testing "Searching without a page starts at the first one"
      (let [[response clj-data] (get-request "/api/search" nil token)
            indices (tdm/extract-thought-idx (map :thought (:results clj-data)))]
        (is response)
        (is (= 200 (:status response)))
        (is (= 0 (:current-page clj-data)))
        (is (= 10 (count indices)))
        (is (= indices (reverse (range 34 44))))
        ))
    (testing "Searching without the third page returns the proper elements"
      (let [[response clj-data] (get-request "/api/search" {:page 2} token)
            indices (tdm/extract-thought-idx (map :thought (:results clj-data)))]
        (is response)
        (is (= 200 (:status response)))
        (is (= 10 (count indices)))
        (is (= indices (reverse (range 14 24))))
        ))
    (testing "Searching without the second page by just GETting memory returns the proper elements"
      (let [[response clj-data] (get-request "/api/thoughts" {:page 1} token)
            indices (tdm/extract-thought-idx (map :thought (:results clj-data)))]
        (is response)
        (is (= 200 (:status response)))
        (is (= 10 (count indices)))
        (is (= indices (reverse (range 24 34))))
        ))
    (testing "We can page while searching with a query"
      (let [[response clj-data] (get-request "/api/search" {:q "memory" :page 0} token)
            results (:results clj-data)
            indices (tdm/extract-thought-idx (map :thought results))]
        (is response)
        (is (= 200 (:status response)))
        (is (= 12 (:total clj-data)))
        (is (= 0 (:current-page clj-data)))
        (is (= 10 (count indices)))
        ))
    (testing "Query pagination works as expected"
      (let [[response clj-data] (get-request "/api/search" {:q "memory" :page 1} token)
            results (:results clj-data)
            indices (tdm/extract-thought-idx (map :thought results))]
        (is response)
        (is (= 200 (:status response)))
        (is (= 12 (:total clj-data)))
        (is (= 1 (:current-page clj-data)))
        (is (= '(33 1) indices))
        ))

    (testing "Sending too far a page returns empty results"
      (let [[response clj-data] (get-request "/api/search" {:q "memory remember" :page 2} token)
            results (:results clj-data)
            indices (tdm/extract-thought-idx (map :thought results))]
        (is response)
        (is (= 200 (:status response)))
        (is (= 14 (:total clj-data)))
        (is (= 0 (count indices)))
        ))

    )
  )



(deftest test-add-memory
  (tdu/init-placeholder-data!)
  (user/create-user! "user1" "password1")
  (let [token (invoke-login {:username "user1" :password "password1"})]
    (testing "Attempting to add a memory without a token results in a 401"
      (testing "We can add a new memory"
        (let [[response _] (post-request "/api/thoughts" {:thought "Just a new idea"} nil)]
          (is (= 401 (:status response)))
          )))
    (testing "We can add a new memory"
      (let [[response record] (post-request "/api/thoughts" {:thought "Just a thought"} token)]
        (is (= 201 (:status response)))
        (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
        (is (map? record))
        (is (:id record))
        (is (= "Just a thought" (:thought record)))
        (is (= (str "http://localhost/api/thoughts/" (:id record)) (get-in response [:headers "Location"])))
        ))
    (testing "After adding a memoy, we can query for it"
      (let [[_ {:keys [total results]}] (get-request "/api/thoughts" nil token)
            item (first results)]
        (is (seq? results))
        (is (= 1 (count results)))
        (is (= 1 total))
        (is (= "user1" (:username item)))
        (is (= "Just a thought" (:thought item)))
        (is (:created item))
        (is (:id item))))
    (testing "We can refine a memory through the API"
      (let [[_ {:keys [results]}] (get-request "/api/thoughts" nil token)
            m1  (first results)
            _   (post-request "/api/thoughts" {:thought "Refining an idea" :refine_id (:id m1)} token)
            [_ {:keys [results]}] (get-request "/api/thoughts" nil token)
            m2  (first results)
            [_ data] (get-request (str "/api/threads/" (:id m1)) nil token)
            ; m1 became a root after m2 was created, so we will expect it to have a root_id when returned
            m1r (assoc m1 :root_id (:id m1))
            ]
        (is m1)
        (is (nil? (:refine_id m1)))
        (is (nil? (:root_id m1)))
        (is (= (:id m1) (:refine_id m2)))
        (is (= (:id m1) (:root_id m2)))
        (is (= data {:results [m1r m2] :id (str (:id m1))}))
        ;; Test that we get an empty list if querying for a thread that does not belong to the user
        (let [new-token (invoke-login {:username tdu/ph-username :password tdu/ph-password})
              [_ data] (get-request (str "/api/threads/" (:id m1)) nil new-token)]
          (is new-token)
          (is (= {:results [] :id (str (:id m1))} data)))
        ))
    )
  (let [token (invoke-login {:username "User1" :password "password1"})]
    (testing "Username on memory addition is not case sensitive"
      (let [[response record] (post-request "/api/thoughts" {:thought "Just a new idea"} token)]
        (is (= 201 (:status response)))
        (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
        (is (map? record))
        (is (:id record))
        (is (= "Just a new idea" (:thought record)))
        )))
  )


(deftest test-add-memory-clean-up
  (tdu/init-placeholder-data!)
  (user/create-user! "user1" "password1")
  (let [token (invoke-login {:username "user1" :password "password1"})]
    (testing "HTML is cleaned up from the saved string"
      (let [[response record] (post-request "/api/thoughts"
                                            {:thought "Just a <b>brilliant!</b> new <i>idea</i><script>and some scripting!</script>\n

                                              **BRILLIANT!**"}
                                            token)]
        (is (= 201 (:status response)))
        (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
        (is (= "Just a brilliant! new idea \n\n\n **BRILLIANT!**" (:thought record)))
        ))
    (testing "After adding a memoy, we can query for it"
      (let [[_ {:keys [total results]}] (get-request "/api/thoughts" nil token)
            item (first results)]
        (is (seq? results))
        (is (= 1 (count results)))
        (is (= 1 total))
        (is (= "user1" (:username item)))
        (is (= "Just a brilliant! new idea \n\n\n **BRILLIANT!**" (:thought item)))
        (is (:created item))
        (is (:id item))))
    ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Memory update and delete
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest test-update-memory
  (tdu/init-placeholder-data!)
  (user/create-user! "user1" "password1")
  (user/create-user! "user2" "password2")
  (let [token-u1 (invoke-login {:username "user1" :password "password1"})
        token-u2 (invoke-login {:username "user2" :password "password2"})]
    (testing "We can update a memory by posting to an ID"
      (let [[_ memory] (post-request "/api/thoughts" {:thought "Memora"} token-u1)
            [_ query1] (get-request "/api/thoughts" nil token-u1)
            [_ updated] (put-request "/api/thoughts" (:id memory) {:thought "Memory"} token-u1)
            [_ query2] (get-request "/api/thoughts" nil token-u1)
            ;; After we have updated it, check that we _aren't_ allowed to do PUT with an ID that does not belog to us
            [ru2 data-ru2] (put-request "/api/thoughts" (:id memory) {:thought "Memories"} token-u2)
            [_ query3] (get-request "/api/thoughts" nil token-u1)
            ]
        (is memory)
        (is (= "Memora" (:thought memory)))
        (is (= "Memora" (:thought (first (:results query1)))))
        (is (= "Memory" (:thought updated)))
        (is (= "Memory" (:thought (first (:results query2)))))
        ;; We still have only one record
        (is (= 1 (:total query2)))
        ;; Verify that we couldn't update it with token-u2
        (is (= 401 (:status ru2)))
        (is (nil? data-ru2))
        (is (= "Memory" (:thought (first (:results query3)))))
        ))
    (testing "Attempting to update a closed memory returns an empty dataset"
      (let [[_ memory] (post-request "/api/thoughts" {:thought "Memora"} token-u1)
            ;; Force the date as if we created it a while ago
            _ (tdb/update-thought-created! *db* (assoc memory :created (c/to-date (.minusMillis (t/now) memory/open-duration))))
            ;; Try to update
            [_ updated] (put-request "/api/thoughts" (:id memory) {:thought "Memory"} token-u1)
            ]
        (is memory)
        (is (= "Memora" (:thought memory)))
        (is (empty? updated))
        ))
    ))


(deftest test-delete-memory
  (tdu/init-placeholder-data!)
  (user/create-user! "user1" "password1")
  (user/create-user! "user2" "password2")
  (let [token-u1 (invoke-login {:username "user1" :password "password1"})
        token-u2 (invoke-login {:username "user2" :password "password2"})]
    (testing "We can update a memory by posting to an ID"
      (let [[_ memory] (post-request "/api/thoughts" {:thought "Memora"} token-u1)
            ; Attempt deleting by an invalid auth token
            [invalid _] (del-request "/api/thoughts" (:id memory) token-u2)
            ; Query before valid delete
            [_ query1] (get-request "/api/thoughts" nil token-u1)
            ; Delete
            [deleted _] (del-request "/api/thoughts" (:id memory) token-u1)
            ; Query post-delete
            [_ query2] (get-request "/api/thoughts" nil token-u1)
            ]
        (is memory)
        (is (= 1 (:total query1)))
        (is (= 401 (:status invalid)))
        (is (= 204 (:status deleted)))
        (is (= 0 (:total query2)))
        ))
    ))