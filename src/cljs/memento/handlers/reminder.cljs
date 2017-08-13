(ns memento.handlers.reminder
  (:require [ajax.core :refer [GET POST PUT]]
            [re-frame.core :refer [dispatch reg-sub reg-event-db subscribe dispatch-sync]]
            [taoensso.timbre :as timbre]
            [memento.helpers :as helpers]))

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
    (assoc-in app-state [:cache :reminders] (helpers/add-html-to-thoughts (not-empty result)))))

(reg-event-db
  :reminder-viewed
  (fn [app-state [_ item]]
    (POST (str "/api/reminders/viewed/" (:id item))
          {:headers       {:authorization (str "Token " (get-in app-state [:credentials :token]))}
           :handler       #(timbre/info "Reminder successfully marked as viewed")
           :error-handler #(dispatch [:state-message (str "Error marking reminder as viewed: " %) "alert-danger"])})
    (let [new-list (not-empty (remove #(= item %) (get-in app-state [:cache :reminders])))]
      (when (not new-list)
        (dispatch [:state-show-reminders false]))
      (assoc-in app-state [:cache :reminders] new-list))))

(reg-event-db
  :reminder-create
  (fn [app-state [_ thought type]]
    (POST "/api/reminders"
          {:params        {:thought-id (:id thought) :type-id type}
           :headers       {:authorization (str "Token " (get-in app-state [:credentials :token]))}
           :handler       #(do
                             ;; TODO: A proper modal would be ideal, maybe removing the reminder button
                             (timbre/info "Reminder created" %)
                             (js/alert "Reminder created")
                             (dispatch [:state-message "Created reminder" "alert-success"]))
           :error-handler #(dispatch [:state-message (str "Error creating reminder as viewed: " %) "alert-danger"])})
    app-state
    ))