(ns memento.handler
  (:require [buddy.auth.backends.token :refer [token-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [bidi.ring :refer [make-handler]]
            [memento.env :refer [defaults]]
            [memento.auth :as auth]
            [memento.middleware :as middleware]
            [memento.routes.api :refer [api-routes not-found-route]]
            [memento.routes.home :refer [home-routes]]
            [mount.core :as mount]))

(mount/defstate init-app
                :start ((or (:init defaults) identity))
                :stop ((or (:stop defaults) identity)))


(defn auth-token-decode
  [_ token]
  (when-let [result (auth/decode-token token)]
    (:username result)))

;; Create an instance of auth backend.

(def auth-backend
  (token-backend {:authfn auth-token-decode}))


(defn app []
  (-> (make-handler ["" [api-routes home-routes not-found-route]])
      ; TODO Wrap-csrf only for the home-routes
      ; middleware/wrap-csrf
      (wrap-authentication auth-backend)
      (wrap-authorization auth-backend)
      middleware/wrap-base
      ))
