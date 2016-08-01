(ns user
  (:require [mount.core :as mount]
            [memento.figwheel :refer [start-fw stop-fw cljs]]
            memento.core))

(defn start []
  (mount/start-without #'memento.core/repl-server))

(defn stop []
  (mount/stop-except #'memento.core/repl-server))

(defn restart []
  (stop)
  (start))


