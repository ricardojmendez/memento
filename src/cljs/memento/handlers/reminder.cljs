(ns memento.handlers.reminder
  (:require [ajax.core :refer [GET POST PUT]]
            [re-frame.core :refer [dispatch reg-sub reg-event-db subscribe dispatch-sync]]
            [taoensso.timbre :as timbre]))

;; Reminder event handlers

(reg-event-db
  :reminder-load
  (fn [app-state _]
    (GET "/api/reminders"
         {:headers       {:authorization (str "Token " (get-in app-state [:credentials :token]))}
          :handler       #(dispatch [:reminder-load-success %])
          :error-handler #(dispatch [:state-message (str "Error loading reminders: " %) "alert-danger"])})
    app-state))

(reg-event-db
  :reminder-load-success
  (fn [app-state [_ result]]
    (timbre/debug "Loaded reminders" result)
    (assoc app-state :reminders (not-empty result))))
