(ns memento.routes.home
  (:require [memento.layout :as layout]
            [ring.util.http-response :refer [ok]]))

(defn home-page [_]
  (layout/render "bootswatch.html"))

(def home-routes ["/" home-page])
