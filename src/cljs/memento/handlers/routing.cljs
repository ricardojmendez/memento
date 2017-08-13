(ns memento.handlers.routing
  (:require [ajax.core :refer [GET POST PUT]]
            [bidi.bidi :as bidi]
            [pushy.core :as pushy]
            [re-frame.core :refer [dispatch reg-sub reg-event-db subscribe dispatch-sync]]
            [taoensso.timbre :as timbre]))

;;;;-------------------------
;;;; Routing
;;;;-------------------------

(def routes ["/" {"record"   :record
                  "remember" :remember
                  "signup"   :signup
                  "login"    :login
                  "thread/"  {[:id] #(dispatch [:thread-display (:id %)])}
                  ""         :record}])

(defn set-page! [match]
  (let [{:keys [handler route-params]} match]
    (timbre/trace "Setting" match)
    (if (fn? handler)
      (handler route-params)
      (dispatch [:state-ui-section handler]))))

(defn bidi-matcher [s]
  (timbre/trace "Matching" s (bidi/match-route routes s))
  (bidi/match-route routes s))

(def history
  (pushy/pushy set-page! bidi-matcher))




;; Handler for changing the browser token from a keyword, so that
;; :record leads to /record. The handler is expected to apply any
;; necessary changes to the ui state, or dispatch the relevant
;; events.
;;
;; TODO: This function might belong in memento.routing instead, it's
;; the only one here messing with bidi, pushy and routing
(reg-event-db
  :state-browser-token
  (fn [app-state [_ token-key]]
    (pushy/set-token! history (bidi/path-for routes token-key))
    app-state))
