(ns memento.routes.api
  (:require [liberator.core
             :refer [defresource resource request-method-in]]
            [clojure.string :refer [lower-case]]
            [compojure.core :refer [defroutes GET ANY]]
            [io.clojure.liberator-transit]
            [cognitect.transit :as transit]
            [memento.db :as db]))


(defresource echo
             :allowed-methods [:get]
             :handle-ok (fn [state]
                          (get-in state [:request :params]))
             :available-media-types ["application/transit+json"
                                     "application/transit+msgpack"
                                     "application/json"])

(defresource memory
             :allowed-methods [:post :get]
             :handle-ok "Hello!"
             ; TODO: Reject empty POSTs. We'll do that once we are also validating it's a registered user.
             :post! (fn [ctx]
                      (let [reader  (transit/reader (get-in ctx [:request :body]) :json)
                            content (transit/read reader)]
                        (when (not-empty content)
                          {:save-result (db/save-memory content)})))
             :handle-created (fn [ctx]
                               {:id (get-in ctx [:save-result :id])})
             :available-media-types ["application/transit+json"
                                     "application/transit+msgpack"
                                     "application/json"])


(defroutes api-routes
           (ANY "/api/echo/:val" [val] echo)
           (ANY "/api/memory" request memory)
           )