(ns memento.auth
  (:require [buddy.sign.jws :as jws]
            [buddy.core.keys :as ks]
            [clj-time.core :as t]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [environ.core :refer [env]]
            [memento.db.user :as user]))

(defn- pkey [auth-conf]
  (ks/private-key
    (io/resource (:privkey auth-conf))
    (:passphrase auth-conf)))

(defn pubkey [auth-conf]
  (ks/public-key
    (io/resource (:pubkey auth-conf))))

(defn create-auth-token
  "Attempts to create an authorization token based on a username and password"
  [username password]
  (let [auth-conf (:auth-conf env)
        valid?    (user/validate-user username password)
        exp       (t/plus (t/now) (t/days 1))]
    (if valid?
      (jws/sign {:username (string/lower-case username)}
                (pkey auth-conf)
                {:alg :rs256 :exp exp}))))


(defn decode-token
  "Decodes and returns a user token. Attempting to decode an expired or
   invalid token will return nil."
  [token]
  (try
    (jws/unsign token (pubkey (:auth-conf env)) {:alg :rs256})
    ;; We don't really care why decoding failed for now
    (catch clojure.lang.ExceptionInfo _ nil)))