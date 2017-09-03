(ns memento.handlers.thread
  (:require [ajax.core :refer [GET POST PUT DELETE]]
            [memento.handlers.auth :refer [clear-token-on-unauth]]
            [re-frame.core :refer [dispatch reg-sub reg-event-db subscribe dispatch-sync]]
            [taoensso.timbre :as timbre]
            [memento.helpers :as helpers]))


;;;
;;; Helpers
;;;

(defn thread-in-cache?
  "Receives an application state and a thread-id, and returns true if the
  application cache currently contains that thread."
  [app-state ^UUID thread-id]
  (contains? (get-in app-state [:cache :threads]) thread-id))


(defn reload-if-cached [app-state ^UUID thread-id]
  (when (thread-in-cache? app-state thread-id)
    (dispatch [:thread-load thread-id])))


;;;
;;; Handlers
;;;


;; This might be better called a train of thought.

(reg-event-db
  ; Separate handler from :thread-load so that we can choose when to display a thread and when to load it.
  :thread-display
  (fn [app-state [_ id]]
    (let [root-id (if (string? id)
                    (uuid id)
                    id)]
      (when (empty? (get-in app-state [:cache :threads root-id]))
        (dispatch [:thread-load root-id]))
      (-> app-state
          (assoc-in [:ui-state :show-thread?] true)
          (assoc-in [:ui-state :show-thread-id] root-id)))))

(reg-event-db
  :thread-load
  (fn [app-state [_ root-id]]
    (let [url (str "/api/threads/" root-id)]
      (GET url {:headers       {:authorization (str "Token " (get-in app-state [:credentials :token]))}
                :handler       #(dispatch [:thread-load-success %])
                :error-handler #(dispatch [:thread-load-error %])}
           ))
    app-state))

(reg-event-db
  :thread-load-error
  (fn [app-state [_ result]]
    ;; Not sure if we actually know which thread failed loading. Probably not if the call failed altogether.
    ;; If we did, we could just assoc the thread to nil.
    (timbre/error "Error loading thread" result)
    (dispatch [:state-message (str "Error loading thread: " result) "alert-danger"])
    app-state))

(reg-event-db
  :thread-load-success
  (fn [app-state [_ {:keys [id results] :as result}]]
    (timbre/debug "Loaded thread" id results)
    (dispatch [:state-ui-section :remember])
    (assoc-in app-state [:cache :threads id] (helpers/add-html-to-thoughts results))))


