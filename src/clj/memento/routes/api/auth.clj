(ns memento.routes.api.auth
  (:require [numergent.auth :as auth]
            [memento.config :refer [env]]
            [memento.db.user :as user]
            [memento.routes.api.common :refer [read-content]]
            [ring.util.http-response :refer [ok unauthorized conflict created]]))


(defn login
  [id pass]
  (if (user/validate id pass)
    (ok (auth/create-auth-token (:auth-conf env) id))
    (unauthorized "Authentication error")))

(defn signup!
  [id pass]
  (let [result (user/create! id pass)]
    (if (:success? result)
      (created "/" (auth/create-auth-token (:auth-conf env) id))
      (conflict "Invalid username/password combination"))))
