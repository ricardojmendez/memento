(ns memento.routes.api
  (:require [buddy.auth :refer [authenticated? throw-unauthorized]]
            [buddy.auth.backends.token :refer [token-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [cognitect.transit :as transit]
            [liberator.core :refer [defresource resource request-method-in]]
            [liberator.representation :refer [ring-response]]
            [io.clojure.liberator-transit]
            [memento.auth :as auth]
            [memento.db.memory :as memory]
            [memento.db.user :as user]
            [numergent.utils :as utils])
  (:import (java.util UUID)))


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
             :allowed-methods [:post :get :put :delete]
             :authorized? (fn [{request :request}]
                            (let [{:keys [identity request-method params]} request
                                  id            (:id params)
                                  has-identity? (not-empty identity)
                                  is-owner?     #(and has-identity?
                                                      id
                                                      (= identity (:username (memory/load-memory (UUID/fromString id)))))]
                              (condp = request-method
                                :get has-identity?
                                :post has-identity?
                                :put (is-owner?)
                                :delete (is-owner?)
                                false)
                              ))
             :handle-ok (fn [{request :request}]
                          (let [query    (:query-params request)
                                username (:identity request)
                                page     (utils/parse-string-number (query "page"))
                                offset   (* page memory/result-limit)]
                            (-> (memory/query-memories username nil offset)
                                (assoc :current-page page)
                                memory/format-created)
                            ))
             :can-put-to-missing? false
             :put! (fn [{{{:keys [id thought]} :params} :request}]
                     {:save-result (memory/update-memory! {:id (UUID/fromString id) :thought thought})})
             :post! (fn [ctx]
                      (let [content  (read-content ctx)
                            username (get-in ctx [:request :identity])]
                        (when (not-empty content)
                          {:save-result (memory/create-memory! (assoc content :username username))})))
             :delete! (fn [{{{:keys [id]} :params} :request}]
                        (memory/delete-memory! (UUID/fromString id)))
             :handle-created (fn [{record :save-result}]
                               (ring-response {:status  201
                                               :headers {"Location" (str "/api/thoughts/" (:id record))}
                                               :body    record}))
             :available-media-types ["application/transit+json"
                                     "application/transit+msgpack"
                                     "application/json"])


(defresource memory-search
             :allowed-methods [:get]
             :authorized? (fn [ctx]
                            (some? (get-in ctx [:request :identity])))
             :handle-ok (fn [{request :request}]
                          (let [query    (:query-params request)
                                username (:identity request)
                                page     (utils/parse-string-number (query "page"))
                                offset   (* page memory/result-limit)]
                            (-> (memory/query-memories username (query "q") offset)
                                (assoc :current-page page)
                                memory/format-created)
                            ))
             :available-media-types ["application/transit+json"
                                     "application/transit+msgpack"
                                     "application/json"])

(defresource thought-thread
             :allowed-methods [:get]
             :authorized? (fn [ctx]
                            (some? (get-in ctx [:request :identity])))
             :handle-ok (fn [{request :request}]
                          (let [id (UUID/fromString (get-in request [:route-params :id]))]
                            (->> id
                                 memory/query-memory-thread
                                 (filter #(= (:username %) (:identity request)))
                                 (hash-map :id id :results)
                                 memory/format-created)))
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
             :post! true                                    ; All the work is done on authorized?
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


(defresource not-found
             :exists? false
             :can-post-to-missing? false
             :available-media-types ["application/transit+json"
                                     "application/transit+msgpack"
                                     "application/json"])


(def api-routes
  ["/api/" {"echo/"       {[:val] echo}
            "auth/login"  login
            "auth/signup" signup
            "thoughts"    memory
            "thoughts/"   {[:id] memory}
            "threads/"    {[:id] thought-thread}
            "search"      memory-search}])

(def not-found-route
  ["/" [[true not-found]]])