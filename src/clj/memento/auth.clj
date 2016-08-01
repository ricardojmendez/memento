(ns memento.auth
  (:require [buddy.sign.jwt :as jwt]
            [buddy.core.keys :as ks]
            [clj-time.core :as t]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [memento.config :refer [env]]
            [memento.db.user :as user]))

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
  "Attempts to create an authorization token based on a username and password"
  [username password]
  (let [auth-conf (:auth-conf env)
        valid?    (user/validate-user username password)
        exp       (t/plus (t/now) (t/days 1))]
    (if valid?
      (jwt/sign {:username (string/lower-case username)}
                (pkey auth-conf)
                {:alg :rs256 :exp exp}))))


(defn decode-token
  "Decodes and returns a user token. Attempting to decode an expired or
   invalid token will return nil."
  [token]
  (try
    (jwt/unsign token (pubkey (:auth-conf env)) {:alg :rs256})
    ;; We don't really care why decoding failed for now
    (catch clojure.lang.ExceptionInfo _ nil)))