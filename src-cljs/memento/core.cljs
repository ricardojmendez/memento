(ns memento.core
  (:require [ajax.core :refer [GET POST PUT]]
            [reagent.core :as reagent :refer [atom]]
            [reagent-forms.core :refer [bind-fields]]
            [reagent.session :as session]
            [re-frame.core :refer [dispatch register-sub register-handler subscribe dispatch-sync]]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [markdown.core :refer [md->html]]
            [ajax.core :refer [GET POST]])
  (:require-macros [reagent.ratom :refer [reaction]])
  (:import goog.History))



;------------------------------
; Queries
;------------------------------

(defn general-query
  [db [sid element-id]]
  (reaction (get-in @db [sid element-id])))

(register-sub :note general-query)
(register-sub :ui-state general-query)




;------------------------------
; Handlers
;------------------------------

(register-handler
  :initialize
  (fn
    [app-state _]
    (merge app-state {:ui-state {:is-busy false}})))

(register-handler
  :log-message
  (fn [app-state [_ msg]]
    (.log js/console (str "Logging: " msg))
    app-state))

(register-handler
  :set-message
  (fn [app-state [_ msg]]
    (.log js/console (str "Will set " msg))
    (assoc-in app-state [:ui-state :last-message] msg)))


(register-handler
  :save-note
  (fn [app-state [_ msg]]
    (POST "/api/memento" {:params {:text msg}
                          :handler #(.log js/console %) #_ #(dispatch [:save-note-done "Success"])
                          :error-handler #(dispatch [:save-note-done (str "Error saving note: " %)])})
    app-state
    ))

(register-handler
  :save-note-done
  (fn [app-state [_ msg]]
    (.log js/console "Done")
    (dispatch [:set-message msg])
    (assoc-in app-state [:ui-state :is-busy] false)))






;------------------------------
; Components
;------------------------------



(def thought-field
  [:textarea {:field       :textarea
              :class       "form-control"
              :placeholder "I was thinking..."
              :rows        12
              :id          :thought
              :style       {:font-size "18px"}
              }])


(defn alert []
  (let [msg (subscribe [:ui-state :last-message])]
    (fn []
      (if (not-empty @msg)
        [:div {:class "alert alert-info"}
         [:button {:type :button :class "close" :on-click #(dispatch [:set-message ""])} "x"]
         [:strong "Heads up! "] @msg]
        )
      )))

(defn write-section []
  (let [doc      (atom {})
        is-busy? (subscribe [:ui-state :is-busy])
        ]
    (fn []
      [:fielset
       [:legend ""
        [:div {:class "form-group"}
         [:div {:class "col-lg-12"}
          [bind-fields thought-field doc]]
         ]
        [:div {:class "form-group"}
         [:div {:class "col-lg-12"}
          [:button {:type "reset" :class "btn btn-default" :on-click #(dispatch [:set-note ""])} "Clear"]
          [:button {:type "submit" :disabled @is-busy? :class "btn btn-primary" :on-click #(dispatch [:save-note (:thought @doc)])} "Submit"]
          ]]
        ]]
      )))






;; -------------------------
;; History
;; must be called after routes have been defined
;; TODO: Figure out how to do that with re-frame
(defn hook-browser-navigation! []
  #_ (doto (History.)
    (events/listen
      EventType/NAVIGATE
      (fn [event]
        (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

;; -------------------------
;; Initialize app
(defn fetch-docs! []
  (GET "/docs" {:handler #(session/put! :docs %)}))

(defn mount-components []
  #_ (reagent/render-component [#'navbar] (.getElementById js/document "navbar"))
  (reagent/render-component [write-section] (.getElementById js/document "write-section"))
  (reagent/render-component [alert] (.getElementById js/document "alert"))
  )

(defn init! []
  (dispatch-sync [:initialize])
  (fetch-docs!)
  (hook-browser-navigation!)
  (mount-components))


