(ns memento.routes.home
  (:require [memento.layout :as layout]
            [compojure.core :refer [defroutes GET]]
            [ring.util.http-response :refer [ok]]))

(defn home-page []
  (layout/render "bootswatch.html"))

(defn about-page []
  (layout/render "about.html"))

(defroutes home-routes
  (GET "/" [] (home-page))
  (GET "/about" [] (about-page))
  (GET "/record" [] (home-page))
  (GET "/login" [] (home-page))
  (GET "/signup" [] (home-page))
  (GET "/remember" [] (home-page))
  (GET "/regard" [] (home-page))
  (GET "/thread/*" [] (home-page)))
