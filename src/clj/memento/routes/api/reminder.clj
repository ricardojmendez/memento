(ns memento.routes.api.reminder
  (:require [liberator.core :refer [defresource resource request-method-in]]
            [liberator.representation :refer [ring-response]]
            [memento.auth :as auth]
            [memento.db.user :as user]
            [memento.routes.api.common :refer [read-content]]
            [memento.db.memory :as memory]
            [memento.db.reminder :as reminder]
            [numergent.utils :as utils])
  (:import (java.util UUID)))

(defresource reminder
  :allowed-methods [:post :get :put :delete]
  :allowed? true
  :authorized? (fn [{request :request}]
                 (let [{:keys [identity request-method params]} request
                       id             (:id params)
                       for-id         (:for-id params)
                       username       (:username identity)
                       has-identity?  (not-empty username)
                       owns-memory?   #(and has-identity?
                                            for-id
                                            (= username (:username (memory/get-by-id (UUID/fromString for-id)))))
                       owns-reminder? #(and has-identity?
                                            id
                                            (= username (:username (reminder/get-by-id (UUID/fromString id)))))]
                   (condp = request-method
                     :get has-identity?
                     :post owns-memory?
                     false)))
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
  :post! (fn [ctx]
           (let [content (read-content ctx)
                 for-id  (UUID/fromString (get-in ctx [:request :params :for-id]))]
             (when (not-empty content)
               {:save-result (reminder/create! (assoc content :thought_id for-id))})))
  :handle-created (fn [{record :save-result}]
                    (ring-response {:status  201
                                    :headers {"Location" (str "/api/reminders/" (:id record))}
                                    :body    record}))
  :available-media-types ["application/transit+json"
                          "application/transit+msgpack"
                          "application/json"])
