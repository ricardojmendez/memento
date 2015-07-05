(ns memento.routes.api
  (:require [buddy.auth :refer [authenticated? throw-unauthorized]]
            [buddy.auth.backends.token :refer [token-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [cognitect.transit :as transit]
            [compojure.core :refer [defroutes GET ANY]]
            [liberator.core :refer [defresource resource request-method-in]]
            [liberator.representation :refer [ring-response]]
            [io.clojure.liberator-transit]
            [memento.auth :as auth]
            [memento.db.memory :as memory]
            [memento.db.user :as user]))


(defn read-content
  "Receives a request context and returns its contents"
  [ctx]
  (let [reader (transit/reader (get-in ctx [:request :body]) :json)]
    (transit/read reader)))


(defresource echo
             :allowed-methods [:get]
             :handle-ok (fn [state]
                          (get-in state [:request :params]))
             :available-media-types ["application/transit+json"
                                     "application/transit+msgpack"
                                     "application/json"])

(defresource memory
             :allowed-methods [:post :get]
             :authorized? (fn [ctx]
                            (some? (get-in ctx [:request :identity])))
             :handle-ok (fn [ctx]
                          (memory/format-created (memory/query-memories (get-in ctx [:request :identity]))))
             ; TODO: Reject empty POSTs. We'll do that once we are also validating it's a registered user.
             :post! (fn [ctx]
                      (let [content  (read-content ctx)
                            username (get-in ctx [:request :identity])]
                        (when (not-empty content)
                          {:save-result (memory/save-memory! (assoc content :username username))})))
             :handle-created (fn [ctx]
                               {:count (:save-result ctx)})
             :available-media-types ["application/transit+json"
                                     "application/transit+msgpack"
                                     "application/json"])


(defresource memory-search
             :allowed-methods [:get]
             :authorized? (fn [ctx]
                            (some? (get-in ctx [:request :identity])))
             :handle-ok (fn [{request :request}]
                          (let [query    (:query-params request)
                                username (:identity request)]
                            (memory/format-created (memory/query-memories username (query "q")))))

             :available-media-types ["application/transit+json"
                                     "application/transit+msgpack"
                                     "application/json"])

(defresource login
             :allowed-methods [:post]
             :authorized? (fn [ctx]
                            (let [content (read-content ctx)
                                  token   (auth/create-auth-token (:username content) (:password content))]
                              (if (not-empty token)
                                {:token token})))
             :post! true                                              ; All the work is done on authorized?
             :handle-created (fn [ctx]
                               {:token (:token ctx)})
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



(defroutes api-routes
           (ANY "/api/echo/:val" [val] echo)
           (ANY "/api/auth/login" request login)
           (ANY "/api/auth/signup" request signup)
           (ANY "/api/memory" request memory)
           (ANY "/api/memory/search" request memory-search))