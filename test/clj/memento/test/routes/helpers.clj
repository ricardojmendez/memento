(ns memento.test.routes.helpers
  (:require [clojure.test :refer :all]
            [clojure.string :as string]
            [cognitect.transit :as transit]
            [memento.handler :refer [app]]
            [ring.mock.request :refer [request header body]]
            [taoensso.timbre :as timbre])
  (:import (java.io ByteArrayOutputStream InputStream)))

;;;;
;;;; Helpers
;;;;

(defn transit->clj
  "Receives a byte array expected to contain transit+json, and coverts it
  to a clojure data structure"
  [arr]
  (if (isa? (class arr) InputStream)                        ; We need isa? because we may get any of InputStream's descendants
    (try
      (let [reader (transit/reader arr :json)]
        (transit/read reader))
      (catch Exception e
        (timbre/error e)
        nil))
    arr))

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
    data))

