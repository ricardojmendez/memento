(ns memento.ui.remember
  (:require [memento.helpers :as helpers]
            [memento.handlers.routing :as r]
            [memento.ui.shared :refer [initial-focus-wrapper panel thought-edit-box top-div-target
                                       Button ButtonGroup DropdownButton
                                       Modal ModalBody ModalFooter
                                       MenuItem OverlayTrigger Tooltip]]
            [re-frame.core :refer [dispatch reg-sub reg-event-db subscribe dispatch-sync]]
            [reagent.core :as reagent])
  (:require-macros [reagent.ratom :refer [reaction]]))


(defn memory-query []
  ;; TODO: Try the new form
  ;; https://lambdaisland.com/blog/11-02-2017-re-frame-form-1-subscriptions
  (let [query     (subscribe [:ui-state :current-query])
        archived? (subscribe [:ui-state :query-all?])
        tooltip   (reagent/as-element [Tooltip {:id :archived?} "Include archived thoughts"])]
    (fn []
      [:div {:class "form-horizontal"}
       [:div {:class "form-group"}
        [:label {:for "input-search" :class "col-md-1 control-label"} "Search:"]
        [:div {:class "col-md-9"}
         [initial-focus-wrapper
          [:input {:type      "text"
                   :class     "form-control"
                   :id        "input-search"
                   :value     @query
                   :on-change #(dispatch-sync [:state-current-query (-> % .-target .-value)])}]]

         ]
        [:div {:class "col-md-2"}
         [OverlayTrigger
          {:placement :left
           :overlay   tooltip}
          [:div {:class "checkbox"}
           [:label
            [:input {:type     "checkbox"
                     :checked  @archived?
                     :on-click #(dispatch-sync [:state-query-all? (not @archived?)])}]
            [:i {:class "fa icon-margin-both fa-archive fa-lg fa-6x"}]]]]]]])))


(defn memory-load-trigger []
  (let [searching?  (subscribe [:ui-state :is-searching?])
        page-index  (subscribe [:search-state :page-index])
        total-pages (reaction (:pages @(subscribe [:search-state :last-result])))]
    (fn []
      (if (< @page-index (dec @total-pages))
        [:div {:style {:text-align "center"}}
         (if @searching?
           [:i {:class "fa fa-spinner fa-spin"}]
           [:i {:class "fa fa-ellipsis-h" :id "load-trigger"}])]))))

(defn list-memories [results show-thread-btn?]
  (let [tooltip (reagent/as-element [Tooltip {:id :forget-thought} [:strong "Forget thought"]])]
    (for [memory results]
      ^{:key (:id memory)}
      [:div {:class "col-sm-12 thought hover-wrapper"}
       [:div {:class "memory col-sm-12"}
        [:span {:on-click #(when (and show-thread-btn? (:root-id memory))
                             (r/match-and-set! (str "/thread/" (:root-id memory))))
                :dangerouslySetInnerHTML
                          {:__html (:html memory)}}]]
       [:div
        [:div {:class "col-sm-8"}
         [ButtonGroup
          (if (empty? (:reminders memory))
            [Button {:bsStyle  "primary"
                     :bsSize   "xsmall"
                     :on-click #(dispatch [:reminder-create memory "spaced"])}
             [:i {:class "fa fa-bell icon-margin-both"}] "Remind"]
            [Button {:bsStyle  "danger"
                     :bsSize   "xsmall"
                     ;; doseq since a thought may have multiple reminders
                     :on-click #(doseq [item (:reminders memory)]
                                  (dispatch [:reminder-cancel item]))}
             [:i {:class "fa fa-bell icon-margin-both"}] "Cancel"])
          [DropdownButton {:title   "..."
                           :bsSize  "xsmall"
                           :class   "btn-primary"
                           :dropup  true
                           :noCaret true}
           ;; Adding the archive option separately so that we don't click it accidentally
           (let [archived?     (:archived? memory)
                 archive-label (if archived? "De-archive" "Archive")
                 archive-class (if archived? "fa-file" "fa-archive")]
             [MenuItem {:on-click #(do (.scrollIntoView top-div-target)
                                       (dispatch [:memory-archive memory (not archived?)]))}
              [:i {:class (str "fa icon-margin-both " archive-class)}]
              archive-label])
           [MenuItem {:divider true}]
           ;; ... then the rest of the menu
           (when (= :open (:status memory))
             [MenuItem {:on-click #(do (dispatch [:state-note :edit-note (:thought memory)])
                                       (dispatch [:memory-edit-set memory]))}
              [:i {:class "fa fa-file-text icon-margin-both"}] "Edit"])
           ;; Do we have a thread?
           (when (and show-thread-btn? (:root-id memory))
             [MenuItem {:href (str "/thread/" (:root-id memory))}
              [:i {:class "fa fa-list-ul icon-margin-both"}] "Train of thought"])
           ;; We can always follow up on a thought, even if it's been archived
           [MenuItem {:on-click #(do (.scrollIntoView top-div-target)
                                     (dispatch [:state-refine memory]))}
            [:i {:class "fa fa-pencil icon-margin-both"}] "Follow up"]
           ]]
         ;; Time stamp and other indicators
         [:i [:small
              (helpers/format-date (:created memory))
              (when (:archived? memory)
                [:i {:class "fa icon-margin-both fa-archive"}])]]]
        [:div {:class "col-sm-4 show-on-hover"
               :style {:text-align "right"}}
         (when (= :open (:status memory))
           [OverlayTrigger
            {:placement :top
             :overlay   tooltip}
            [:span {:class    "btn btn-danger btn-xs icon-margin-left"
                    :on-click #(dispatch [:memory-forget memory])}
             [:i {:class "fa fa-remove"}]]
            ])]

        ]])))

(defn edit-memory []
  (let [edit-memory (subscribe [:note :edit-memory])
        note        (subscribe [:note :edit-note])
        is-busy?    (subscribe [:ui-state :is-busy?])
        ;; On the next one, we can't use not-empty because (= nil (not-empty nil)), and :show expects true/false,
        ;; not a truth-ish value.
        show?       (reaction (not (empty? @edit-memory)))]
    (fn []
      [Modal {:show @show? :onHide #(dispatch [:memory-edit-set nil])}
       [ModalBody
        [:div {:class "col-sm-12 thought"}
         [thought-edit-box :edit-note]]]
       [ModalFooter
        [:button {:type     "submit"
                  :class    "btn btn-primary"
                  :disabled (or @is-busy? (empty? @note))
                  :on-click #(dispatch [:memory-edit-save])} "Save"]
        ;; May want to add a style to show the Cancel button only on mobile, as the same functionality can
        ;; easily be triggered on desktop by pressing Esc
        [:button {:type     "reset"
                  :class    "btn btn-default"
                  :on-click #(dispatch [:memory-edit-set nil])} "Cancel"]
        ]])))


(defn memory-thread []
  (let [show?     (subscribe [:ui-state :show-thread?])
        thread-id (subscribe [:ui-state :show-thread-id])
        ;; I subscribe to the whole thread cache because I can't just subscribe to [:threads @thread-id],
        ;; as it'd only be evaluated once. I tried to do a reaction with the path, then subscribe
        ;; to the @path... but the subscription is not refreshed when the @path changes.
        threads   (subscribe [:cache :threads])
        thread    (reaction (get @threads @thread-id))
        ready?    (reaction (and (not-empty @thread) @show?))]
    (fn []
      [Modal {:show @ready? :onHide #(dispatch [:state-show-thread false])}
       [ModalBody
        (list-memories @thread false)]
       [ModalFooter
        [:button {:type     "reset"
                  :class    "btn btn-default"
                  :on-click #(dispatch [:state-show-thread false])} "Close"]]])))

(defn memory-results []
  (let [busy?   (subscribe [:ui-state :is-searching?])
        results (subscribe [:search-state :list])]
    (fn []
      [panel (if @busy?
               [:span "Loading..." [:i {:class "fa fa-spin fa-space fa-circle-o-notch"}]]
               "Thoughts")
       [:span
        (if (empty? @results)
          [:p "Nothing."]
          (list-memories @results true))
        [memory-load-trigger]
        ]
       "panel-primary"])))

(defn memory-list []
  (fn []
    [:span
     [edit-memory]
     [memory-thread]
     [memory-query]
     [memory-results]]))