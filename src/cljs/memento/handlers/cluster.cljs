(ns memento.handlers.cluster
  (:require [ajax.core :refer [GET POST PUT PATCH]]
            [re-frame.core :refer [dispatch reg-sub reg-event-db reg-event-fx subscribe dispatch-sync]]
            [taoensso.timbre :as timbre]
            [memento.helpers :as helpers]))

(reg-event-fx
  :cluster-create
  (fn [{:keys [db]} [_ thought-ids]]
    (POST "/api/clusters"
          {:params        {:thought-ids thought-ids}
           :headers       {:authorization (str "Token " (get-in db [:credentials :token]))}
           :handler       #(do
                             (timbre/info "Saved cluster" %)
                             (dispatch [:state-message "Created cluster" "alert-success"]))
           :error-handler #(dispatch [:state-message (str "Error creating thought cluster: " %) "alert-danger"])})
    nil))