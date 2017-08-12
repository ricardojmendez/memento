(ns memento.env
  (:require [selmer.parser :as parser]
            [clojure.tools.logging :as log]))

(def defaults
  {:init       (fn []
                 (parser/cache-off!)
                 (log/info "\n-=[memento started successfully using the test profile]=-"))
   :stop       (fn []
                 (log/info "\n-=[memento has shut down successfully]=-"))
   :middleware identity})
