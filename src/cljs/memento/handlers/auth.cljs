(ns memento.handlers.auth
  (:require [ajax.core :refer [GET POST PUT]]
            [memento.handlers.routing :as r]
            [re-frame.core :refer [dispatch reg-sub reg-event-db subscribe dispatch-sync]]
            [taoensso.timbre :as timbre]
            [reagent.cookies :as cookies]))



;;;
;;; Functions
;;;

(defn clear-token-on-unauth
  "Receives an application state and an authorization result. If the status is 401, then it
  dispatches a message to clear the authorization token."
  [result]
  (if (= 401 (:status result))
    (dispatch [:auth-set-token nil])))


;;;
;;; Handlers
;;;

(reg-event-db
  :auth-request
  (fn [app-state [_ signup?]]
    (let [url     (if signup? "signup" "login")
          to-send (if signup? [:username :password :password2] [:username :password])
          ;; Should probably centralize password validation, so we can use the same function
          ;; both here and when the UI is being updated
          valid?  (or (not signup?)
                      (= (get-in app-state [:credentials :password])
                         (get-in app-state [:credentials :password2])))]
      (when valid?
        (POST (str "/api/auth/" url) {:params        (->> (select-keys (:credentials app-state) to-send)
                                                          ;; Get only the non-null values
                                                          (filter (comp some? val))
                                                          (into {}))
                                      :handler       #(dispatch [:auth-set-token %])
                                      :error-handler #(dispatch [:auth-request-error %])}))
      )
    (assoc-in app-state [:ui-state :wip-login?] true)))

(reg-event-db
  :auth-set-token
  (fn [app-state [_ token]]
    (if (not-empty token)
      (dispatch [:state-message ""]))
    (cookies/set! :token token)
    (timbre/trace "Current state" (r/bidi-matcher (-> js/window .-location .-pathname)))
    (cond
      (empty? token) (dispatch [:state-ui-section :login])
      (= :signup (get-in app-state [:ui-state :section])) (dispatch [:state-ui-section :record])
      :else (r/set-page! (r/bidi-matcher (-> js/window .-location .-pathname))))
    (-> app-state
        (assoc-in [:credentials :token] token)
        (assoc-in [:ui-state :wip-login?] false)
        (assoc-in [:credentials :password] nil)
        (assoc-in [:credentials :password2] nil))))


(reg-event-db
  :auth-request-error
  (fn [app-state [_ result]]
    (timbre/info "Auth error" (str result))
    (let [status    (:status result)
          is-unauth (or (= 401 status) (= 409 status))
          message   (if is-unauth "Invalid username/password" (:status-text result))
          msg-type  (if is-unauth "alert-danger" "alert-warning")]
      (-> app-state
          (assoc-in [:ui-state :wip-login?] false)
          (assoc-in [:credentials :message] {:text message :type msg-type})
          (assoc-in [:credentials :token] nil)
          (assoc-in [:credentials :password] nil)
          (assoc-in [:credentials :password2] nil)))
    ))

(reg-event-db
  :auth-validate
  (fn [app-state _]
    (when-let [token (get-in app-state [:credentials :token])]
      (GET "/api/auth/validate" {:headers       {:authorization (str "Token " token)}
                                 :handler       #(dispatch [:auth-set-token %])
                                 :error-handler #(dispatch [:auth-request-error %])}))
    app-state))