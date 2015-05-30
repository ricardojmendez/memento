(ns memento.core
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent-forms.core :refer [bind-fields]]
            [reagent.session :as session]
            [re-frame.core :refer [dispatch register-sub register-handler]]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [markdown.core :refer [md->html]]
            [ajax.core :refer [GET POST]])
  (:import goog.History))



(register-handler
  :log-message
  (fn [app-state [_ msg]]
    (.log js/console (str "Logging: " msg))
    app-state))


(def thought-field
  [:textarea {:field :textarea :class "form-control" :placeholder "I was thinking..." :rows 12 :id :thought}])

(defn write-section []
  (let [doc (atom {})]
    (fn []
      [:fielset
       [:legend "Write!"
        [:div {:class "form-group"}
         [:div {:class "col-lg-12"}
          [bind-fields thought-field doc]]
         ]
        [:div {:class "form-group"}
         [:div {:class "col-lg-12"}
          [:button {:type "reset" :class "btn btn-default" :on-click #(dispatch [:log-message "Canceled"])} "Clear"]
          [:button {:type "submit" :class "btn btn-primary" :on-click #(dispatch [:log-message @doc])} "Submit"]
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
  (reagent/render-component [write-section] (.getElementById js/document "write-section")))

(defn init! []
  (fetch-docs!)
  (hook-browser-navigation!)
  (mount-components))


