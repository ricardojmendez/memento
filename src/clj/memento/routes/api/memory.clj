(ns memento.routes.api.memory
  (:require [liberator.core :refer [defresource resource request-method-in]]
            [liberator.representation :refer [ring-response]]
            [memento.auth :as auth]
            [memento.db.user :as user]
            [memento.routes.api.common :refer [read-content]]
            [memento.db.memory :as memory]
            [numergent.utils :as utils])
  (:import (java.util UUID)))

(defresource memory
  :allowed-methods [:post :get :put :delete]
  :authorized? (fn [{request :request}]
                 (let [{:keys [identity request-method params]} request
                       id            (:id params)
                       username      (:username identity)
                       has-identity? (not-empty username)
                       is-owner?     #(and has-identity?
                                           id
                                           (= username (:username (memory/get-by-id (UUID/fromString id)))))]
                   (condp = request-method
                     :get has-identity?
                     :post has-identity?
                     :put (is-owner?)
                     :delete (is-owner?)
                     false)
                   ))
  :handle-ok (fn [{request :request}]
               (let [query    (:query-params request)
                     username (get-in request [:identity :username])
                     page     (utils/parse-string-number (query "page"))
                     offset   (* page memory/result-limit)]
                 (-> (memory/query username nil offset)
                     (assoc :current-page page)
                     memory/format-created)
                 ))
  :can-put-to-missing? false
  :put! (fn [{{{:keys [id thought]} :params} :request}]
          {:save-result (memory/update! {:id (UUID/fromString id) :thought thought})})
  :post! (fn [ctx]
           (let [content  (read-content ctx)
                 username (get-in ctx [:request :identity :username])]
             (when (not-empty content)
               {:save-result (memory/create! (assoc content :username username))})))
  :delete! (fn [{{{:keys [id]} :params} :request}]
             (memory/delete! (UUID/fromString id)))
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
                     username (get-in request [:identity :username])
                     page     (utils/parse-string-number (query "page"))
                     offset   (* page memory/result-limit)]
                 (-> (memory/query username (query "q") offset)
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
               (let [id-str (get-in request [:route-params :id])
                     id     (UUID/fromString id-str)]
                 (->> id
                      memory/query-thread
                      (filter #(= (:username %) (get-in request [:identity :username])))
                      ;; I'll return the id as a string so that the frontend doesn't
                      ;; have to do any parsing guesswork.
                      (hash-map :id id-str :results)
                      memory/format-created)))
  :available-media-types ["application/transit+json"
                          "application/transit+msgpack"
                          "application/json"])