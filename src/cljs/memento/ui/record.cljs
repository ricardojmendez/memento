(ns memento.ui.record
  (:require [memento.ui.shared :refer [initial-focus-wrapper panel thought-edit-box top-div-target]]
            [re-frame.core :refer [dispatch reg-sub reg-event-db subscribe dispatch-sync]])
  (:require-macros [reagent.ratom :refer [reaction]]))


(defn reminder-section [is-focused?]
  (let [reminders (subscribe [:cache :reminders])
        showing?  (subscribe [:ui-state :show-reminders?])]
    (fn []
      ;; We will only show the reminder notification when we aren't focused
      ;; on elaborating a thought
      (if (and @reminders @showing?)
        ;; Reminder list
        ;; TODO Should probably extract to a component
        [:div {:id "reminder-list" :class "well well-sm"}
         [:a {:on-click #(dispatch [:state-show-reminders false])}
          [:i {:style {:top       "0px"
                       :right     "5px"
                       :font-size "24px"
                       :z-index   999
                       :position  "absolute"}
               :class "fa fa-window-close"}]]
         (for [item (sort-by :created @reminders)]
           ;; Need to figure out if the reminder has a day-idx in its schedule,
           ;; since legacy reminders will not.  See #73.
           (let [day-idx    (get-in item [:properties :day-idx])
                 days       (get-in item [:properties :days])
                 total-days (count days)
                 days-left? (and (some? day-idx)            ; Could be nil
                                 (< day-idx (dec total-days)))
                 next-leap  (when days-left?
                              (nth days (inc day-idx)))
                 label      (cond
                              next-leap "Viewed"
                              (nil? day-idx) "Viewed"       ; Thought about labeling it differently, let's not confuse users
                              :else "Done")
                 thought    (:thought-record item)]
             ;; TODO: Would be ideal to display a tooltip with the number of
             ;; days until the next reminder, but OverlayTrigger/Tooltip are
             ;; barfing with an error. Tabling.
             ^{:key (:id item)}
             [:div {:class "row reminder-item hover-wrapper"}
              [:div {:class "col-sm-12"}
               [:span {:dangerouslySetInnerHTML {:__html (:html item)}}]]
              [:div {:class "col-sm-12 show-on-hover"}
               [:div {:class "show-on-hover col-sm-4"}
                [:span {:class    "btn btn-success btn-xs icon-margin-left"
                        :on-click #(dispatch [:reminder-viewed item])}
                 [:i {:class "fa fa-check"} " " label]]
                (when (or next-leap (nil? day-idx))
                  [:span {:class    "btn btn-danger btn-xs icon-margin-left"
                          :on-click #(dispatch [:reminder-cancel item])}
                   [:i {:class "fa fa-trash"} "Cancel"]])]
               ;; Showing only the buttons for Elaborate and Thread. I don't want to get into the potential mess
               ;; of editing or removing a thought while the reminder is shown yet.
               [:span {:class "col-sm-8" :style {:text-align "right"}}
                (if (:root-id thought)
                  [:a {:class "btn btn-primary btn-xs"
                       :href  (str "/thread/" (:root-id thought))}
                   [:i {:class "fa fa-list-ul icon-margin-both"}] "Train of thought"])
                [:a {:class    "btn btn-primary btn-xs"
                     :on-click #(do (.scrollIntoView top-div-target)
                                    (dispatch [:state-refine thought]))}
                 [:i {:class "fa fa-pencil icon-margin-both"}] "Follow up"]

                ]]

              ]))
         ]
        ;; Reminder notice
        (when (and (not-empty @reminders)
                   (not @is-focused?))
          [:div {:class    "alert alert-info"
                 :on-click #(dispatch [:state-show-reminders true])}
           [:p
            [:strong "Hi!"]
            " "
            "You have some thoughts you wanted to be reminded of."]
           [:p
            [:b "Click this section when you are ready to read them."]]]))
      )))


(defn write-section []
  (let [note        (subscribe [:note :current-note])
        is-busy?    (subscribe [:ui-state :is-busy?])
        focus       (subscribe [:note :focus])
        is-focused? (reaction (not-empty @focus))]
    (dispatch-sync [:reminder-load])
    (fn []
      [:div
       [reminder-section is-focused?]
       [:fielset
        [:div {:class "form-horizontal"}
         [thought-edit-box :current-note]
         [:div {:class "form-group"}
          [:div {:class "col-sm-12" :style {:text-align "right"}}
           [:button {:type     "submit"
                     :disabled (or @is-busy? (empty? @note))
                     :class    "btn btn-primary"
                     :on-click #(dispatch [:memory-save])}
            "Record"]
           ]]]]])))

