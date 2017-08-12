(ns memento.handler
  (:require [compojure.core :refer [routes wrap-routes]]
            [compojure.route :as route]
            [memento.layout :refer [error-page]]
            [memento.routes.home :refer [home-routes]]
            [memento.routes.api :refer [service-routes]]
            [memento.env :refer [defaults]]
            [mount.core :as mount]
            [memento.middleware :as middleware]))

(mount/defstate init-app
  :start ((or (:init defaults) identity))
  :stop  ((or (:stop defaults) identity)))

(def app-routes
  (routes
    (-> #'home-routes
        (wrap-routes middleware/wrap-csrf)
        (wrap-routes middleware/wrap-formats))
    #'service-routes
    (route/not-found
      (:body
        (error-page {:status 404
                     :title "page not found"})))))


(defn app [] (middleware/wrap-base #'app-routes))
