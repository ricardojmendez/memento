(ns memento.core
  (:require [jayq.core :refer [$]]
            [reagent.cookies :as cookies]
            [re-frame.core :refer [dispatch reg-sub reg-event-db subscribe dispatch-sync]]
            [memento.handlers.cache]
            [memento.handlers.memory]
            [memento.handlers.reminder]
            [memento.handlers.resolution]
            [memento.handlers.routing :as r]
            [memento.handlers.ui-state]
            [memento.handlers.thread]
            [memento.ui.login :refer [login-form]]
            [memento.ui.record :refer [write-section]]
            [memento.ui.remember :refer [memory-list]]
            [memento.ui.resolve :refer [resolution-list]]
            [memento.ui.shared :refer [navbar alert]]
            [pushy.core :as pushy]
            [reagent.core :as reagent]
            [taoensso.timbre :as timbre]))


;;;;------------------------------
;;;; Queries
;;;;------------------------------

(defn general-query
  [db [sid element-id]]
  (get-in db [sid element-id]))

(reg-sub :note general-query)
(reg-sub :cache general-query)
(reg-sub :ui-state general-query)
(reg-sub :search-state general-query)
(reg-sub :credentials general-query)


;;;;------------------------------
;;;; Handlers
;;;;------------------------------


(reg-event-db
  :initialize
  (fn [app-state _]
    (merge app-state {:ui-state {:is-busy?        false
                                 :wip-login?      false
                                 :show-thread?    false
                                 :section         :record
                                 :current-query   ""
                                 :query-all?      false
                                 :results-page    0
                                 :memories        {:pages 0}
                                 :show-reminders? false
                                 :is-searching?   false}
                      :cache    {}                          ; Will be used for caching threads and reminders
                      :note     {:edit-memory nil}
                      })))



;;;;------------------------------
;;;; Components
;;;;------------------------------


(defn content-section []
  (let [section (subscribe [:ui-state :section])
        token   (subscribe [:credentials :token])]
    (if (and (nil? @token)
             (not= :login @section)
             (not= :signup @section))
      (dispatch-sync [:state-ui-section :login]))
    (case @section
      :record [write-section]
      :remember [memory-list]
      :resolve [resolution-list]
      [login-form])))

(defn header []
  (let [state (subscribe [:ui-state :section])
        label (case @state
                :record "Record a new thought"
                :remember "Remember"
                :resolve "Resolutions"
                "")]
    (if (not-empty label)
      [:h1 {:id "forms"} label])))


;; -------------------------
;; Initialize app


(defn add-on-appear-handler
  "Adds an event that dispatches a memory loader when an element comes into
  view. Expects an element id as parameter and a function"
  [id f]
  (let [e ($ id)]
    (.appear e)
    (.on ($ js/document.body) "appear" id f)))

(defn mount-components []
  (reagent/render-component [navbar] (.getElementById js/document "navbar"))
  (reagent/render-component [content-section] (.getElementById js/document "content-section"))
  (reagent/render-component [alert] (.getElementById js/document "alert"))
  (reagent/render-component [header] (.getElementById js/document "header")))

(defn init! []
  (timbre/set-level! :debug)
  (pushy/start! r/history)
  (dispatch-sync [:initialize])
  (dispatch-sync [:auth-set-token (cookies/get :token nil)])
  (dispatch-sync [:auth-validate])
  (add-on-appear-handler "#load-trigger" #(dispatch [:memories-load-next]))
  (mount-components))