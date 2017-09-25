(ns memento.test.routes.api.thought
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
  (:import (java.util Date)))

(use-fixtures
  :once
  (fn [f]
    (mount/start
      #'memento.config/env
      #'memento.db.core/*db*)
    (f)))

;;;;
;;;; Tests
;;;;


;;;
;;; Memory search and creation
;;;


;; Start by testing addition
(deftest test-add-memory
  (tdu/init-placeholder-data!)
  (user/create! "user1" "password1")
  (let [token (invoke-login {:username "user1" :password "password1"})]
    (testing "Attempting to add a memory without a token results in a 400"
      (let [[response _] (post-request "/api/thoughts" {:thought "Just a new idea"} nil)]
        (is (= 400 (:status response)))))
    (testing "We cannot add empty thoughts"
      (let [[response _] (post-request "/api/thoughts" {:thought ""} token)]
        (is (= 400 (:status response))))
      ;; Empty string is trimmed
      (let [[response _] (post-request "/api/thoughts" {:thought "  "} token)]
        (is (= 400 (:status response)))))
    (testing "We can add a new memory"
      (let [[response record] (post-request "/api/thoughts" {:thought "Just a thought"} token)]
        (is (= 201 (:status response)))
        (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
        (is (map? record))
        (is (:id record))
        (is (= "Just a thought" (:thought record)))
        (is (= (str "http://localhost/api/thoughts/" (:id record)) (get-in response [:headers "Location"])))
        ))
    (testing "After adding a memory, it shows up on the query results"
      (let [[_ {:keys [total results]}] (get-request "/api/thoughts" nil token)
            item (first results)]
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
            m1r (assoc m1 :root_id (:id m1) :reminders [])
            ]
        (is m1)
        (is (nil? (:refine_id m1)))
        (is (nil? (:root_id m1)))
        (is (= (:id m1) (:refine_id m2)))
        (is (= (:id m1) (:root_id m2)))
        (is (= {:id      (:id m1)
                :results [m1r m2]}
               data))
        ;; Test that we get an empty list if querying for a thread that does not belong to the user
        (let [new-token (invoke-login {:username tdu/ph-username :password tdu/ph-password})
              [_ data] (get-request (str "/api/threads/" (:id m1)) nil new-token)]
          (is new-token)
          (is (= {:results [] :id (:id m1)} data)))
        ))
    (testing "After adding a memory, we can request it directly"
      (let [[_ item-post] (post-request "/api/thoughts" {:thought "Just a thought to retrieve"} token)
            [resp item-get] (get-request (str "/api/thoughts/" (:id item-post)) nil token)]
        (is (= 200 (:status resp)))
        (is (= item-post item-get))))
    (testing "After adding a memory, we can only see it from the creator"
      (user/create! "user2" "password2")
      (let [token-u2 (invoke-login {:username "user2" :password "password2"})
            [_ item-post] (post-request "/api/thoughts" {:thought "Just a thought to retrieve"} token)
            [resp item-get] (get-request (str "/api/thoughts/" (:id item-post)) nil token)
            [resp-bad item-bad] (get-request (str "/api/thoughts/" (:id item-post)) nil token-u2)]
        ;; We can't read it with an invalid token
        (is (= 404 (:status resp-bad)))
        (is (nil? item-bad))
        ;; ... but the valid token responds as expected
        (is (= 200 (:status resp)))
        (is (= item-post item-get))))
    )
  (let [token (invoke-login {:username "User1" :password "password1"})]
    (testing "Username on memory addition is not case sensitive"
      (let [[response record] (post-request "/api/thoughts" {:thought "Just a new idea"} token)]
        (is (= 201 (:status response)))
        (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
        (is (map? record))
        (is (:id record))
        (is (= "Just a new idea" (:thought record)))
        ))))

(deftest test-add-memory-clean-up
  (tdu/init-placeholder-data!)
  (user/create! "user1" "password1")
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
        (is (= 1 (count results)))
        (is (= 1 total))
        (is (= "user1" (:username item)))
        (is (= "Just a brilliant! new idea \n\n\n **BRILLIANT!**" (:thought item)))
        (is (:created item))
        (is (:id item))))
    ))



;; For some reason this test is sometimes outputing the result of the search call,
;; but I haven't been able to find where I'm doing it. I don't see any log or
;; println. Even a single call to search, or get on thoughts, does it.
;;
;; Same thing happens with test-query-memories on memento.test.db.memory
(deftest test-search-memory
  (tdu/init-placeholder-data!)
  (tdm/import-placeholder-memories!)
  (let [token (invoke-login {:username tdu/ph-username :password tdu/ph-password})]
    (testing "Search request should not include a trailing slash"
      (let [[response _] (get-request "/api/search/?q=")]
        (is response)
        (is (= 400 (:status response)))))
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
          (is (= Date (type (:created e)))))
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
          (is (= Date (type (:created e)))))
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
  (user/create! "user1" "ssh!")
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
          (is (= Date (type (:created e))))
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


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Memory update and delete
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest test-update-memory-text
  (tdu/init-placeholder-data!)
  (user/create! "user1" "password1")
  (user/create! "user2" "password2")
  (let [token-u1 (invoke-login {:username "user1" :password "password1"})
        token-u2 (invoke-login {:username "user2" :password "password2"})]
    (testing "We can update a thought's text by patching to an ID"
      (let [[_ memory] (post-request "/api/thoughts" {:thought "Memora"} token-u1)
            [_ query1] (get-request "/api/thoughts" nil token-u1)
            [_ updated] (patch-request "/api/thoughts" (:id memory) {:thought "Memory"} token-u1)
            [_ query2] (get-request "/api/thoughts" nil token-u1)
            ;; After we have updated it, check that we _aren't_ allowed to do PATCH with an ID that does not belong to us
            [ru2 data-ru2] (patch-request "/api/thoughts" (:id memory) {:thought "Memories"} token-u2)
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
        (is (= 404 (:status ru2)))
        (is (nil? data-ru2))
        (is (= "Memory" (:thought (first (:results query3)))))
        ))
    (testing "Sending a nil thought raises an error"
      (let [[_ memory] (post-request "/api/thoughts" {:thought "Memora"} token-u1)
            [_ loaded-1] (get-request (str "/api/thoughts/" (:id memory)) nil token-u1)
            [result _] (patch-request "/api/thoughts" (:id memory) {:thought nil} token-u1)
            [_ loaded-2] (get-request (str "/api/thoughts/" (:id memory)) nil token-u1)
            ]
        (is memory)
        (is (= "Memora" (:thought memory)))
        (is (= "Memora" (:thought loaded-1)))
        (is (= 400 (:status result)))
        (is (= loaded-1 loaded-2))
        ))
    (testing "Sending a blank thought does not alter anything"
      (let [[_ memory] (post-request "/api/thoughts" {:thought "Memora"} token-u1)
            [_ loaded-1] (get-request (str "/api/thoughts/" (:id memory)) nil token-u1)
            [result updated] (patch-request "/api/thoughts" (:id memory) {:thought " "} token-u1)
            [_ loaded-2] (get-request (str "/api/thoughts/" (:id memory)) nil token-u1)
            ]
        (is memory)
        (is (= "Memora" (:thought memory)))
        (is (= "Memora" (:thought loaded-1)))
        (is (= 403 (:status result)))
        (is (= "Cannot blank a thought's text" updated))
        (is (= loaded-1 loaded-2))
        ))
    (testing "Not sending thought information at all raises an error"
      (let [[_ memory] (post-request "/api/thoughts" {:thought "Memora"} token-u1)
            [_ loaded-1] (get-request (str "/api/thoughts/" (:id memory)) nil token-u1)
            [result _] (patch-request "/api/thoughts" (:id memory) {} token-u1)
            [_ loaded-2] (get-request (str "/api/thoughts/" (:id memory)) nil token-u1)
            ]
        (is memory)
        (is (= "Memora" (:thought memory)))
        (is (= "Memora" (:thought loaded-1)))
        (is (= 400 (:status result)))
        (is (= loaded-1 loaded-2))
        ))
    (testing "Attempting to update a closed memory returns an empty dataset"
      (let [[_ memory] (post-request "/api/thoughts" {:thought "Memora"} token-u1)
            ;; Force the date as if we created it a while ago
            _ (tdb/update-thought-created! *db* (assoc memory :created (c/to-date (.minusMillis (t/now) memory/open-duration))))
            ;; Try to update
            [response updated] (patch-request "/api/thoughts" (:id memory) {:thought "Memory"} token-u1)
            ]
        (is memory)
        (is (= "Memora" (:thought memory)))
        (is (= 403 (:status response)))
        (is (= "Cannot update closed thoughts" updated))
        ))
    ))


(deftest test-archive-memory
  (tdu/init-placeholder-data!)
  (user/create! "user1" "password1")
  (user/create! "user2" "password2")
  (let [token-u1 (invoke-login {:username "user1" :password "password1"})
        token-u2 (invoke-login {:username "user2" :password "password2"})]
    (testing "We can archive a thought using PUT to archive"
      (let [[_ memory] (post-request "/api/thoughts" {:thought "Memora"} token-u1)
            [_ query1] (get-request "/api/thoughts" nil token-u1)
            [_ updated-1] (put-request "/api/thoughts" (:id memory) "archive" {:archived? true} token-u1)
            [_ query2] (get-request "/api/thoughts" nil token-u1)
            ;; After we have updated it, check that we _aren't_ allowed to do PUT with an ID that does not belong to us
            [ru2 data-ru2] (put-request "/api/thoughts" (:id memory) "archive" {:archived? false} token-u2)
            [_ after-invalid] (get-request (str "/api/thoughts/" (:id updated-1)) nil token-u1)
            ;; De-archive
            [_ updated-2] (put-request "/api/thoughts" (:id memory) "archive" {:archived? false} token-u1)
            [_ updated-3] (put-request "/api/thoughts" (:id memory) "archive" {:archived? false} token-u1)
            ]
        ;; Check the original state
        (is memory)
        (is (false? (:archived? memory)))
        (is (= "Memora" (:thought memory)))
        (is (= "Memora" (:thought (first (:results query1)))))
        ;; Check the updated state
        (is (= "Memora" (:thought updated-1)))
        (is (:archived? updated-1))
        ;; We have no records, as it doesn't return archived thoughts by  default
        (is (= 0 (:total query2)))
        ;; Verify that we couldn't update it with token-u2
        (is (= 404 (:status ru2)))
        (is (nil? data-ru2))
        ;; Status should still be archived
        (is (:archived? after-invalid))
        (is (= updated-1 after-invalid))
        ;; De-archiving a thought works as expected
        (is (= updated-2
               (assoc updated-1 :archived? false)))
        ;; De-archiving is idempotent
        (is (= updated-2 updated-3))
        ))
    (testing "Archiving thoughts on a thread does not affect the thread"
      (let [[_ memory] (post-request "/api/thoughts" {:thought "To follow up"} token-u1)
            [_ refine] (post-request "/api/thoughts" {:thought "Following up on an idea" :refine_id (:id memory)} token-u1)
            [_ query1] (get-request "/api/thoughts" nil token-u1)
            [_ updated-1] (put-request "/api/thoughts" (:id memory) "archive" {:archived? true} token-u1)
            [_ query2] (get-request "/api/thoughts" nil token-u1)
            [_ thread] (get-request (str "/api/threads/" (:id memory)) nil token-u1)
            ]
        ;; Check the original state
        (is memory)
        (is (false? (:archived? memory)))
        (is (= "To follow up" (:thought memory)))
        (is (= (:id memory) (:root_id refine)))
        ;; Check the updated state
        (is (:archived? updated-1))
        ;; The result on query are as expected
        (is (= 3 (:total query1)))
        (is (= 2 (:total query2)))
        ;; The follow-up thought wasn't affected
        (is (false? (:archived? (memory/get-by-id (:id refine)))))
        ;; The thread returns the archived thoughts
        (is (= (map :id [memory refine])
               (map :id (:results thread))))
        (is (= [true false]
               (map :archived? (:results thread))))
        ))
    ))

(deftest test-query-archived-memories
  (tdu/init-placeholder-data!)
  (user/create! "user1" "password1")
  (user/create! "user2" "password2")
  (let [token-u1 (invoke-login {:username "user1" :password "password1"})
        token-u2 (invoke-login {:username "user2" :password "password2"})]
    (testing "We can archive a thought using PUT to archive"
      (let [[_ memory] (post-request "/api/thoughts" {:thought "Memora"} token-u1)
            [_ query1] (get-request "/api/thoughts" nil token-u1)
            [_ updated-1] (put-request "/api/thoughts" (:id memory) "archive" {:archived? true} token-u1)
            [_ query2] (get-request "/api/thoughts" nil token-u1)
            ;; After we have updated it, check that we _aren't_ allowed to do PUT with an ID that does not belong to us
            [ru2 data-ru2] (put-request "/api/thoughts" (:id memory) "archive" {:archived? false} token-u2)
            [_ after-invalid] (get-request (str "/api/thoughts/" (:id updated-1)) nil token-u1)
            ;; De-archive
            [_ updated-2] (put-request "/api/thoughts" (:id memory) "archive" {:archived? false} token-u1)
            [_ updated-3] (put-request "/api/thoughts" (:id memory) "archive" {:archived? false} token-u1)]
        ;; Check the original state
        (is memory)
        (is (false? (:archived? memory)))
        (is (= "Memora" (:thought memory)))
        (is (= "Memora" (:thought (first (:results query1)))))
        ;; Check the updated state
        (is (= "Memora" (:thought updated-1)))
        (is (:archived? updated-1))
        ;; We have no records, as it doesn't return archived thoughts by  default
        (is (= 0 (:total query2)))
        ;; Verify that we couldn't update it with token-u2
        (is (= 404 (:status ru2)))
        (is (nil? data-ru2))
        ;; Status should still be archived
        (is (:archived? after-invalid))
        (is (= updated-1 after-invalid))
        ;; De-archiving a thought works as expected
        (is (= updated-2
               (assoc updated-1 :archived? false)))
        ;; De-archiving is idempotent
        (is (= updated-2 updated-3))
        ))
    ))

(deftest test-delete-memory
  (tdu/init-placeholder-data!)
  (user/create! "user1" "password1")
  (user/create! "user2" "password2")
  (let [token-u1 (invoke-login {:username "user1" :password "password1"})
        token-u2 (invoke-login {:username "user2" :password "password2"})]
    (testing "We can delete a thought"
      (let [[_ memory] (post-request "/api/thoughts" {:thought "Memora"} token-u1)
            ;; Attempt deleting by an invalid auth token
            [invalid _] (del-request "/api/thoughts" (:id memory) token-u2)
            ;; Query before valid delete
            [_ query1] (get-request "/api/thoughts" nil token-u1)
            ;; Delete and re-query
            [deleted _] (del-request "/api/thoughts" (:id memory) token-u1)
            [_ query2] (get-request "/api/thoughts" nil token-u1)
            [response item] (get-request (str "/api/thoughts/" (:id memory)) nil token-u1)
            ]
        (is memory)
        (is (= 1 (:total query1)))
        (is (= 404 (:status invalid)))
        (is (= 204 (:status deleted)))
        ;; Ensure that it's neither available through querying or direct GET
        (is (= 0 (:total query2)))
        (is (= 404 (:status response)))
        (is (nil? item))
        ))
    (testing "We cannot delete closed thoughts"
      (let [[_ memory] (post-request "/api/thoughts" {:thought "Memora"} token-u1)
            ;; Force the date as if we created it a while ago
            _ (tdb/update-thought-created! *db* (assoc memory :created (c/to-date (.minusMillis (t/now) memory/open-duration))))
            ;; Query before valid delete
            [_ query1] (get-request "/api/thoughts" nil token-u1)
            ;; Delete and re-query
            [response body] (del-request "/api/thoughts" (:id memory) token-u1)
            [_ query2] (get-request "/api/thoughts" nil token-u1)]
        (is memory)
        (is (= 1 (:total query1)))
        (is (= 403 (:status response)))
        (is (= "Cannot delete closed thoughts" body))
        (is (= 1 (:total query2)))
        ))
    ))
