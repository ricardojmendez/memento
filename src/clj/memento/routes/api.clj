(ns memento.routes.api
  (:require [liberator.core
             :refer [defresource resource request-method-in]]
            [clojure.string :refer [lower-case]]
            [compojure.core :refer [defroutes GET ANY]]
            [io.clojure.liberator-transit]
            [cognitect.transit :as transit]
            [memento.auth :as auth]
            [memento.db.memory :as memory]
            ))


(defn read-content
  "Receives a request context and returns its contents"
  [ctx]
  (let [reader  (transit/reader (get-in ctx [:request :body]) :json)]
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
             :handle-ok (fn [_]
                          (memory/format-created (memory/query-memories)))
             ; TODO: Reject empty POSTs. We'll do that once we are also validating it's a registered user.
             :post! (fn [ctx]
                      (let [content (read-content ctx)]
                        (when (not-empty content)
                          {:save-result (memory/save-memory! content)})))
             :handle-created (fn [ctx]
                               {:count (:save-result ctx)})
             :available-media-types ["application/transit+json"
                                     "application/transit+msgpack"
                                     "application/json"])


(defresource memory-search
             :allowed-methods [:get]
             :handle-ok (fn [{{query :query-params} :request}]
                          (memory/format-created (memory/query-memories (query "q"))))

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



(defroutes api-routes
           (ANY "/api/echo/:val" [val] echo)
           (ANY "/api/auth/login" request login)
           (ANY "/api/memory" request memory)
           (ANY "/api/memory/search" request memory-search))