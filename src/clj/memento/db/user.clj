(ns memento.db.user
  (:require [clojure.string :refer [lower-case]]
            [environ.core :refer [env]]
            [buddy.hashers :as hashers]
            [memento.db.core :as db])
  (:import (org.postgresql.util PSQLException)))



(defn create-user!
  "Saves a new username, hashing the password as it does so"
  [^String username ^String password]
  ;; We could use bouncer for validation here if we get the params as a map
  ;; or just convert them ourselves for validation
  (cond
    (empty? password) {:success? false :message "A non-empty password is required"}
    (empty? username) {:success? false :message "A non-empty username is required"}
    :else (try
            (let [encrypted (hashers/encrypt password)
                  record    {:username (lower-case username) :password encrypted}]
              (db/run db/create-user! record)
              (assoc record :success? true))
            (catch PSQLException e
              {:success? false :message (.getMessage e)}))))


(defn validate-user
  "Receives a username and password and returns true if they validate to an actual user"
  [^String username ^String password]
  (if (or (empty? username)
          (empty? password))
    false
    (let [record (first (db/run db/get-user {:username (lower-case username)}))]
      (hashers/check password (:password record)))))