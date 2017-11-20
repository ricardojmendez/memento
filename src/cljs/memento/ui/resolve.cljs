(ns memento.ui.resolve
  (:require [memento.ui.shared :refer [initial-focus-wrapper panel thought-edit-box top-div-target]]
            [re-frame.core :refer [dispatch reg-sub reg-event-db subscribe dispatch-sync]])
  (:require-macros [reagent.ratom :refer [reaction]]))


(defn resolution-header []
  [:div {:id "header" :class "well well-sm"}
   [:p "This is an experimental decision diary."]
   [:p "Press the button below to record a new decision."]
   [:button {:type     "submit"
             :class    "btn btn-primary"
             :on-click #(dispatch [:resolution-new])}
    "New Resolution"]])

(defn resolution-list []
  [:div {:id "header" :class "well well-sm"}
   [:p "This is an experimental decision diary. "
    "You can register your decisions so you can revisit them in the future."]
   [:p "Press the button below to record a new decision."]
   [:button {:type     "submit"
             :class    "btn btn-primary"
             :on-click #(dispatch [:resolution-new])}
    "New Resolution"]]

  )