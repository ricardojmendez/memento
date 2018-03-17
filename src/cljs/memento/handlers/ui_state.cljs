(ns memento.handlers.ui-state
  (:require [ajax.core :refer [GET POST PUT]]
            [memento.handlers.auth :refer [clear-token-on-unauth]]
            [re-frame.core :refer [dispatch reg-sub reg-event-db subscribe dispatch-sync]]
            [taoensso.timbre :as timbre]))

(reg-event-db
  :state-credentials
  (fn [app-state [_ k v]]
    (assoc-in app-state [:credentials k] v)))

(reg-event-db
  :state-ui-section
  (fn [app-state [_ section]]
    (timbre/trace :state-ui-section section)
    (condp = section
      :remember (dispatch [:memories-load])
      :regard (dispatch [:clusters-load-all])
      nil)
    ; Do not associate nil sections
    (if (some? section)
      (assoc-in app-state [:ui-state :section] section)
      app-state)))

(reg-event-db
  :state-message
  (fn [app-state [_ msg class]]
    (let [message {:text msg :class class}]
      ;; TODO: Consider changing this for a keyword
      (if (= class "alert-success")
        (js/setTimeout #(dispatch [:state-message-if-same message nil]) 3000))
      (assoc-in app-state [:ui-state :last-message] message))))

(reg-event-db
  :state-message-if-same
  (fn [app-state [_ compare-msg new-msg]]
    (if (= compare-msg (get-in app-state [:ui-state :last-message]))
      (assoc-in app-state [:ui-state :last-message] new-msg)
      app-state
      )))

(reg-event-db
  :state-note
  (fn [app-state [_ note-id note]]
    (assoc-in app-state [:note note-id] note)))

(reg-event-db
  :state-current-query
  (fn [app-state [_ q]]
    (dispatch [:memories-load 0])
    (assoc-in app-state [:ui-state :current-query] q)))

(reg-event-db
  :state-query-all?
  (fn [app-state [_ archived?]]
    (dispatch [:memories-load 0])
    (assoc-in app-state [:ui-state :query-all?] archived?)))

(reg-event-db
  :state-refine
  (fn [app-state [_ thought]]
    (dispatch [:state-browser-token :record])
    (dispatch [:state-show-reminders false])
    (assoc-in app-state [:note :focus] thought)))

(reg-event-db
  :state-show-reminders
  (fn [app-state [_ show?]]
    ;; Trivial for now, since I'm not juggling a lot of decisions
    (assoc-in app-state [:ui-state :show-reminders?] show?)))

(reg-event-db
  :state-show-thread
  (fn [app-state [_ show? return-to]]
    (when-not show? (dispatch [:state-browser-token return-to]))
    (assoc-in app-state [:ui-state :show-thread?] show?)))

(reg-event-db
  :state-error
  (fn [app-state [_ message result]]
    (timbre/error message result)
    (dispatch [:state-message (str message ": " result) "alert-danger"])
    (clear-token-on-unauth result)
    (assoc-in app-state [:ui-state :is-busy?] false)))