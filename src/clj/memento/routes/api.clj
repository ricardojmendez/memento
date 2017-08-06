(ns memento.routes.api
  (:require [buddy.auth :refer [authenticated? throw-unauthorized]]
            [buddy.auth.backends.token :refer [token-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [liberator.core :refer [defresource resource request-method-in]]
            [liberator.representation :refer [ring-response]]
            [io.clojure.liberator-transit]
            [memento.routes.api.auth :as auth]
            [memento.routes.api.common :refer [read-content]]
            [memento.routes.api.memory :as memory]
            [numergent.utils :as utils])
  (:import (java.util UUID)))


(defresource echo
  :allowed-methods [:get]
  :handle-ok (fn [state]
               (get-in state [:request :params]))
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
  ["/api/" {"echo/"         {[:val] echo}
            "auth/login"    auth/login
            "auth/signup"   auth/signup
            "auth/validate" auth/validate
            "thoughts"      memory/memory
            "thoughts/"     {[:id] memory/memory}
            "threads/"      {[:id] memory/thought-thread}
            "search"        memory/memory-search}])

(def not-found-route
  ["/" [[true not-found]]])