(ns memento.env
  (:require [clojure.tools.logging :as log]
            [luminus-migrations.core :as migrations]
            [memento.config :refer [env]]))

(def defaults
  {:init
               (fn []
                 (do
                   (log/info "-=[ Applying migrations ]=-")
                   (migrations/migrate ["migrate"] (select-keys env [:database-url]))
                   (log/info "...migrations done")
                   (log/info "\n-=[memento started successfully]=-")))
   :stop
               (fn []
                 (log/info "\n-=[memento has shut down successfully]=-"))
   :middleware identity})
