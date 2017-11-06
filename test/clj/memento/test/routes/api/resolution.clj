(ns memento.test.routes.api.resolution
  (:require [clojure.test :refer :all]
            [clj-time.coerce :as c]
            [clj-time.core :as t]
            [memento.handler :refer [app]]
            [memento.config :refer [env]]
            [memento.db.user :as user]
            [memento.test.db.user :as tdu]
            [memento.test.routes.helpers :refer [post-request patch-request get-request put-request del-request invoke-login]]
            [memento.db.core :refer [*db*]]
            [mount.core :as mount]))


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

(deftest test-resolution-add-query
  (tdu/init-placeholder-data!)
  (user/create! "user1" "password1")
  (user/create! "user2" "password1")
  (let [token       (invoke-login {:username "user1" :password "password1"})
        other-token (invoke-login {:username "user2" :password "password1"})]
    (testing "Attempting to add a resolution without a token results in a 400"
      (let [[response _] (post-request "/api/resolutions"
                                       {:subject     "A decision!"
                                        :description "Although maybe..."}
                                       nil)]
        (is (= 400 (:status response)))))
    (testing "We cannot add empty resolutions"
      (let [[response _] (post-request "/api/resolutions"
                                       {:subject ""}
                                       token)]
        (is (= 400 (:status response))))
      ;; Empty string is trimmed
      (let [[response _] (post-request "/api/resolutions"
                                       {:subject ""}
                                       token)]
        (is (= 400 (:status response))))
      (let [[response r] (post-request "/api/resolutions"
                                       {:subject     ""
                                        :description ""}
                                       token)]
        (is (= 400 (:status response))))
      (let [[response _] (post-request "/api/resolutions"
                                       {:subject     "A subject"
                                        :description " "}
                                       token)]
        (is (= 400 (:status response)))))
    (testing "We can add a new resolution"
      (let [[response record] (post-request "/api/resolutions"
                                            {:subject     "A decision!"
                                             :description "Although maybe..."}
                                            token)]
        (is (= 201 (:status response)))
        (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
        (is (map? record))
        (is (:id record))
        (is (= {:description  "Although maybe..."
                :tags         nil
                :username     "user1"
                :alternatives nil
                :archived?    false
                :subject      "A decision!"
                :outcomes     nil}
               (dissoc record :created :id)))))
    (testing "After adding a resolution, it shows up on the query results"
      (let [[response results] (get-request "/api/resolutions" nil token)
            item (first results)]
        (is (= 200 (:status response)))
        (is (= 1 (count results)))
        (is (= "user1" (:username item)))
        (is (= "Although maybe..." (:description item)))
        (is (:created item))
        (is (:id item))))
    (testing "Tags are converted to lowercase"
      (let [[response record] (post-request "/api/resolutions"
                                            {:subject     "A decision!"
                                             :description "Although maybe..."
                                             :tags        "idea, Privacy"}
                                            token)]
        (is (= 201 (:status response)))
        (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
        (is (map? record))
        (is (:id record))
        (is (= {:description  "Although maybe..."
                :tags         "idea, privacy"
                :username     "user1"
                :alternatives nil
                :archived?    false
                :subject      "A decision!"
                :outcomes     nil}
               (dissoc record :created :id)))
        (is (= (str "http://localhost/api/resolutions/" (:id record)) (get-in response [:headers "Location"])))))
    (testing "We clean up HTML"
      (let [[response record] (post-request "/api/resolutions"
                                            {:subject      "<blink>A decision!</blink>"
                                             :description  "Although <i>maybe</i> this one..."
                                             :tags         "idea, <script/>Privacy, latest"
                                             :alternatives "<html>dunno"
                                             :outcomes     "Something good <small>(I hope)</small>"}
                                            token)]
        (is (= 201 (:status response)))
        (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
        (is (map? record))
        (is (:id record))
        (is (= {:description  "Although maybe this one..."
                :tags         "idea, privacy, latest"
                :username     "user1"
                :alternatives "dunno"
                :archived?    false
                :subject      "A decision!"
                :outcomes     "Something good (I hope)"}
               (dissoc record :created :id)))
        (is (= (str "http://localhost/api/resolutions/" (:id record)) (get-in response [:headers "Location"])))))
    (testing "We get as many resolutions as we've added on the results"
      (let [[response results] (get-request "/api/resolutions" nil token)
            item (first results)]
        (is (= 200 (:status response)))
        (is (= 3 (count results)))
        (is (= "user1" (:username item)))
        (is (= "Although maybe this one..." (:description item)))))
    (testing "We get no resolutions from a different token"
      (let [[response results] (get-request "/api/resolutions" nil other-token)]
        (is (= 200 (:status response)))
        (is (= 0 (count results)))))
    (testing "We get no resolutions without a token"
      (let [[response] (get-request "/api/resolutions" nil nil)]
        (is (= 400 (:status response)))))
    (testing "We can request a single item"
      (let [[response record] (post-request "/api/resolutions"
                                            {:subject     "<blink>A decision!</blink>"
                                             :description "Let's retrieve it"
                                             :outcomes    "We hope to get it back"}
                                            token)]
        (is (= 201 (:status response)))
        (is (= "application/transit+json" (get-in response [:headers "Content-Type"])))
        (is (= record
               (second (get-request (str "/api/resolutions/" (:id record)) nil token))))))
    (testing "We can't request a resolution from someone else's token"
      (let [[_ item] (post-request "/api/resolutions"
                                   {:subject     "<blink>A decision!</blink>"
                                    :description "Let's retrieve it"
                                    :outcomes    "We hope to get it back"}
                                   token)
            [response _] (get-request (str "/api/resolutions/" (:id item)) nil other-token)
            ]
        (is (= 404 (:status response)))))
    (testing "We can't request a resolution without a token"
      (let [[_ item] (post-request "/api/resolutions"
                                   {:subject     "<blink>A decision!</blink>"
                                    :description "Let's retrieve it"
                                    :outcomes    "We hope to get it back"}
                                   token)
            [response body] (get-request (str "/api/resolutions/" (:id item)) nil nil)]
        (is (= 400 (:status response)))
        (is (= {:errors {:authorization 'missing-required-key}}
               body))))
    ))
