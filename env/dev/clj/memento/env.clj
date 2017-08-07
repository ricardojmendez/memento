(ns memento.env
  (:require [selmer.parser :as parser]
            [taoensso.timbre :as timbre]
            [memento.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init       (fn []
                 (parser/cache-off!)
                 (timbre/info "\n-=[memento started successfully using the development profile]=-"))
   :stop       (fn []
                 (timbre/info "\n-=[memento has shut down successfully]=-"))
   :middleware wrap-dev})
