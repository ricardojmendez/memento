(ns memento.handlers.routing
  (:require [ajax.core :refer [GET POST PUT]]
            [bidi.bidi :as bidi]
            [pushy.core :as pushy]
            [re-frame.core :refer [dispatch reg-sub reg-event-db reg-event-fx
                                   subscribe dispatch-sync]]
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

(defn set-page!
  "Sets a page to the handler matching a parameter. The handler can be either
  a keyword or a function.

  In case the match is a keyword, it'll assume it's a UI section we need to
  switch to. If it's a function, it will invoke it with the route-params."
  [match]
  (let [{:keys [handler route-params]} match]
    (timbre/trace "Setting" match)
    (if (fn? handler)
      (handler route-params)
      (dispatch [:state-ui-section handler]))))

(defn bidi-matcher
  "Will match a URL to a route"
  [s]
  (timbre/trace "Matching" s (bidi/match-route routes s))
  (bidi/match-route routes s))


(def history
  (pushy/pushy set-page! bidi-matcher))

(defn match-and-set!
  "Matches a URL to a route, sets the page, and pushes it on the state"
  [url]
  (pushy/set-token! history url)
  (set-page! (bidi-matcher url)))

;; Handler for changing the browser token from a keyword, so that
;; :record leads to /record. The handler is expected to apply any
;; necessary changes to the ui state, or dispatch the relevant
;; events.
(reg-event-fx
  :state-browser-token
  (fn [_ [_ token-key]]
    (pushy/set-token! history (bidi/path-for routes token-key))
    nil))
