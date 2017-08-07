(ns numergent.auth
  (:require [buddy.sign.jwt :as jwt]
            [buddy.core.keys :as ks]
            [clj-time.core :as t]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [schema.core :as s]
            [taoensso.timbre :as timbre])
  (:import (clojure.lang ExceptionInfo)))

;; TODO: I should really extract this to a library

(def AuthConf
  {:passphrase String
   :pubkey     String
   :privkey    String})

(defn- pkey
  "Receives a dictionary containing privkey and passphrase. If privkey
  ends in .pem, it's assumed to be a key path. Otherwise, it's expected
  to be the actual key string."
  [{:keys [privkey passphrase]}]
  (if (string/ends-with? privkey ".pem")
    (ks/private-key (io/resource privkey) passphrase)
    (ks/str->private-key privkey passphrase)))


(defn pubkey [auth-conf]
  (ks/public-key
    (io/resource (:pubkey auth-conf))))


(defn create-auth-token
  "Creates a token for a username. By default, the token has a validity of one day.
  Does not take care of user validation, it's assumed that the caller will do
  this."
  ([auth-conf id]
   (create-auth-token auth-conf id (t/plus (t/now) (t/months 1))))
  ([auth-conf id expiration]
   (s/validate AuthConf auth-conf)
   (jwt/sign {:username (string/lower-case id)}
             (pkey auth-conf)
             {:alg :rs256 :exp expiration})))


(defn decode-token
  "Decodes and returns a user token. Attempting to decode an expired or
   invalid token will return nil."
  [auth-conf token]
  (s/validate AuthConf auth-conf)
  (try
    (jwt/unsign token (pubkey auth-conf) {:alg :rs256})
    ;; We don't really care why decoding failed for now
    (catch ExceptionInfo e
      (timbre/trace e "Token decode error")
      nil)))


(defn decode-for-buddy
  "Function to be used for buddy's wrap-authentication. It'll decode
  a token, return the associated username, expiration and the token
  itself."
  [auth-conf _ token]
  (s/validate AuthConf auth-conf)
  (when-let [result (decode-token auth-conf token)]
    (assoc result :token token)))