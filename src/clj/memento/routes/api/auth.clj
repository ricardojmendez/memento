(ns memento.routes.api.auth
  (:require [liberator.core :refer [defresource resource request-method-in]]
            [liberator.representation :refer [ring-response]]
            [memento.auth :as auth]
            [memento.db.user :as user]
            [memento.routes.api.common :refer [read-content]]))

(defresource login
  :allowed-methods [:post]
  :authorized? (fn [ctx]
                 (let [content (read-content ctx)
                       token   (auth/create-auth-token (:username content) (:password content))]
                   (if (not-empty token)
                     {:token token})))
  :post! true                                               ; All the work is done on authorized?
  :handle-created (fn [ctx]
                    {:token (:token ctx)})
  :available-media-types ["application/transit+json"
                          "application/transit+msgpack"
                          "application/json"])

(defresource validate
  :allowed-methods [:get]
  :authorized? (fn [ctx]
                 (some? (get-in ctx [:request :identity])))
  :handle-ok (fn [ctx]
               {:token (get-in ctx [:request :identity :token])})
  :available-media-types ["application/transit+json"
                          "application/transit+msgpack"
                          "application/json"])


(defresource signup
  :allowed-methods [:post]
  :authorized? (fn [ctx]
                 ;; Only allow a sign up if there is no known identity
                 (nil? (get-in ctx [:request :identity])))
  :post! (fn [ctx]
           (let [content (read-content ctx)
                 created (user/create-user! (:username content) (:password content))]
             (if (:success? created)
               {::token (auth/create-auth-token (:username content) (:password content))})))
  :handle-created (fn [{token ::token}]
                    (if (empty? token)
                      (ring-response {:status 409 :body {:error "Invalid username/password combination"}})
                      {:token token}
                      ))
  :available-media-types ["application/transit+json"
                          "application/transit+msgpack"
                          "application/json"])
