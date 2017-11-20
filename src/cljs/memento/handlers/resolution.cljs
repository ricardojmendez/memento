(ns memento.handlers.resolution
  (:require [ajax.core :refer [GET POST PUT PATCH DELETE]]
            [memento.handlers.auth :refer [clear-token-on-unauth]]
            [memento.helpers :as helpers]
            [re-frame.core :refer [dispatch reg-sub reg-event-db subscribe dispatch-sync]]
            [taoensso.timbre :as timbre]))

(reg-event-db
  :resolution-load
  (fn [app-state _]
    (GET "/api/resolutions" {:headers       {:authorization (str "Token " (get-in app-state [:credentials :token]))}
                             :handler       #(dispatch [:resolution-load-success %])
                             :error-handler #(dispatch [:state-error "Error remembering" %])
                             })
    app-state))

(reg-event-db
  :resolution-load-success
  (fn [app-state [_ event]]
    (timbre/debug "Loaded" event)
    app-state))