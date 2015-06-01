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
  (fn [app-state _]
    (merge app-state {:ui-state {:is-busy false :section :write}})))

(register-handler
  :set-ui-section
  (fn [app-state [_ section]]
    (assoc-in app-state [:ui-state :section] section)))

(register-handler
  :log-message
  (fn [app-state [_ msg]]
    (.log js/console (str "Logging: " msg))
    app-state))

(register-handler
  :set-message
  (fn [app-state [_ msg class]]
    (assoc-in app-state [:ui-state :last-message] {:text msg :class class})))

(register-handler
  :update-note
  (fn [app-state [_ note]]
    (assoc-in app-state [:note :current-note] note)))


(register-handler
  :save-note
  (fn [app-state _]
    (let [note (get-in app-state [:note :current-note])]
      (POST "/api/memory" {:params        {:text note}
                           :handler       #(dispatch [:save-note-success note])
                           :error-handler #(dispatch [:save-note-error (str "Error saving note: " %)])}))
    app-state
    ))

(register-handler
  :save-note-success
  (fn [app-state [_ msg]]
    (.log js/console "Done")
    (dispatch [:set-message (str "Saved: " msg) "alert-success"])
    (-> app-state
        (assoc-in [:ui-state :is-busy] false)
        (assoc-in [:note :current-note] "")
        )))

(register-handler
  :save-note-error
  (fn [app-state [_ msg]]
    (dispatch [:set-message (str "Error saving note: " msg) "alert-danger"])
    (assoc-in app-state [:ui-state :is-busy] false)
    ))


;------------------------------
; Components
;------------------------------


(defn navbar-item
  "Renders a navbar item. Having each navbar item have its own subscription will probably
  have a bit of overhead, but I don't imagine it'll be anything major since we won't have
  more than a couple of them."
  [name section]
  (let [current     (subscribe [:ui-state :section])
        is-current? (reaction (= section @current))
        class       (when @is-current? "active")]
    [:li {:class class} [:a {:on-click #(dispatch [:set-ui-section section])} name
                         (if @is-current?
                           [:span {:class "sr-only"} "(current)"])]]))


(defn navbar []
  [:nav {:class "navbar navbar-default navbar-fixed-top"}
   [:div {:class "container-fluid"}
    [:div {:class "navbar-header"}
     [:a {:class "navbar-brand"} "Memento"]
     ]
    [:div {:class "collapse navbar-collapse" :id "navbar-items"}
     [:ul {:class "nav navbar-nav"}
      [navbar-item "Write" :write]
      [navbar-item "Remember" :remember]
      ]]
    ]
   ])


(defn alert []
  (let [msg (subscribe [:ui-state :last-message])]
    (fn []
      (if (not-empty (:text @msg))
        [:div {:class (str "alert " (:class @msg))}
         [:button {:type :button :class "close" :on-click #(dispatch [:set-message ""])} "x"]
         (:text @msg)]
        )
      )))


(defn write-section []
  (let [note      (subscribe [:note :current-note])
        is-busy?  (subscribe [:ui-state :is-busy])]
    (fn []
      [:fielset
       [:div {:class "form-horizontal"}
        [:div {:class "form-group"}
         [:div {:class "col-lg-12"}
          [:textarea {:class       "form-control"
                      :placeholder "I was thinking..."
                      :rows        12
                      :style       {:font-size "18px"}
                      :on-change   #(dispatch-sync [:update-note (-> % .-target .-value)])
                      :value       @note
                      }]
          ]]
        [:div {:class "form-group"}
         [:div {:class "col-lg-12"}
          [:button {:type "reset" :class "btn btn-default" :on-click #(dispatch [:update-note ""])} "Clear"]
          [:button {:type "submit" :disabled (or @is-busy? (empty? @note)) :class "btn btn-primary" :on-click #(dispatch [:save-note])} "Submit"]
          ]]
        ]]
      )))


(defn content-section []
  (let [current (subscribe [:ui-state :section])]
    (condp = @current
      :write [write-section]
      :remember [:p]
      )
    )
  )






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
  (reagent/render-component [navbar] (.getElementById js/document "navbar"))
  (reagent/render-component [content-section] (.getElementById js/document "content-section"))
  (reagent/render-component [alert] (.getElementById js/document "alert"))
  )

(defn init! []
  (dispatch-sync [:initialize])
  (fetch-docs!)
  (hook-browser-navigation!)
  (mount-components))


