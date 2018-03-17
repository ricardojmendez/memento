(ns memento.core
  (:require [ajax.core :refer [GET POST PUT]]
            [bidi.bidi :as bidi]
            [clojure.string :refer [trim split]]
            [cljsjs.react-bootstrap]
            [reagent.cookies :as cookies]
            [reagent.core :as reagent :refer [atom]]
            [re-frame.core :refer [dispatch reg-sub reg-event-db subscribe dispatch-sync]]
            [jayq.core :refer [$]]
            [markdown.core :refer [md->html]]
            [memento.handlers.auth :refer [clear-token-on-unauth]]
            [memento.handlers.cache]
            [memento.handlers.memory]
            [memento.handlers.reminder]
            [memento.handlers.routing :as r]
            [memento.handlers.ui-state]
            [memento.handlers.thread]
            [memento.helpers :as helpers]
            [pushy.core :as pushy]
            [ajax.core :refer [GET POST PUT DELETE]]
            [taoensso.timbre :as timbre
             :refer-macros [log trace debug info warn error fatal report
                            logf tracef debugf infof warnf errorf fatalf reportf
                            spy get-env]])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [memento.misc.cljs-macros :refer [adapt-bootstrap]]))


;;;;------------------------------
;;;; Data and helpers
;;;;------------------------------


(adapt-bootstrap Button)
(adapt-bootstrap ButtonGroup)
(adapt-bootstrap DropdownButton)
(adapt-bootstrap MenuItem)
(adapt-bootstrap OverlayTrigger)
(adapt-bootstrap Popover)
(adapt-bootstrap Tooltip)
(adapt-bootstrap Navbar)
(adapt-bootstrap Navbar.Header)
(adapt-bootstrap Navbar.Brand)
(adapt-bootstrap Navbar.Toggle)
(adapt-bootstrap Navbar.Collapse)
(adapt-bootstrap Nav)
(adapt-bootstrap NavItem)
(def Modal (reagent/adapt-react-class js/ReactBootstrap.Modal))
(def ModalBody (reagent/adapt-react-class js/ReactBootstrap.ModalBody))
(def ModalFooter (reagent/adapt-react-class js/ReactBootstrap.ModalFooter))


(defn find-dom-elem
  "Find a dom element by its id. Expects a keyword."
  [id]
  (first ($ id)))

(def top-div-target (find-dom-elem :#header))


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


(def initial-focus-wrapper
  (with-meta identity
             {:component-did-mount #(.focus (reagent/dom-node %))}))

(defn navbar-item
  "Renders a navbar item. Having each navbar item have its own subscription will probably
  have a bit of overhead, but I don't imagine it'll be anything major since we won't have
  more than a couple of them.

  It will use the section id to get the route to link to."
  [label section]
  (let [current     (subscribe [:ui-state :section])
        is-current? (reaction (= section @current))
        class       (when @is-current? "active")]
    [NavItem {:class    class
              :eventKey section
              :href     (bidi/path-for r/routes section)}
     label
     (when @is-current?
       [:span {:class "sr-only"} "(current)"])]))


(defn navbar []
  (let [token (subscribe [:credentials :token])]
    (fn []
      [Navbar {:collapseOnSelect true
               :fixedTop         true}
       [Navbar.Header
        ;; Oddly, if the :a is inside the Navbar.Brand, it ends up being converted to a span
        ;; Not quite sure what the hiccup rule is in that case
        [:a {:href "/about"}
         [Navbar.Brand "Memento"]]
        [Navbar.Toggle]]
       [Navbar.Collapse
        (if (nil? @token)
          [Nav
           [navbar-item "Login" :login]
           [navbar-item "Sign up" :signup]]
          [Nav
           [navbar-item "Record" :record]
           [navbar-item "Remember" :remember]])
        [Nav {:pullRight true}
         [NavItem {:href "/about"} "About"]]
        ]
       ]
      )))


(defn alert []
  (let [msg (subscribe [:ui-state :last-message])]
    (fn []
      (if (not-empty (:text @msg))
        [:div {:class (str "alert " (:class @msg))}
         [:button {:type :button :class "close" :on-click #(dispatch [:state-message ""])} "x"]
         (:text @msg)]
        )
      )))


(defn focused-thought []
  (let [focus (subscribe [:note :focus])]
    (if @focus
      [:div {:class "col-sm-10 col-sm-offset-1"}
       [:div {:class "panel panel-default"}
        [:div {:class "panel-heading"} "Following up ... " [:i [:small "(from " (helpers/format-date (:created @focus)) ")"]]
         [:button {:type "button" :class "close" :aria-hidden "true" :on-click #(dispatch [:state-refine nil])} "×"]]
        [:div {:class "panel-body"}
         [:p {:dangerouslySetInnerHTML {:__html (:html @focus)}}]
         ]]])
    ))


(defn thought-edit-box [note-id]
  (let [note (subscribe [:note note-id])]
    (fn []
      [:div {:class "form-group"}
       [focused-thought]
       [:div {:class "col-sm-12"}
        [initial-focus-wrapper
         [:textarea {:class       "form-control"
                     :id          "thought-area"
                     :placeholder "I was thinking..."
                     :rows        12
                     :style       {:font-size "18px"}
                     :on-change   #(dispatch-sync [:state-note note-id (-> % .-target .-value)])
                     :value       @note
                     }]]
        ]]))
  )

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

(defn panel [title msg class]
  [:div {:class (str "panel " class)}
   [:div {:class "panel-heading"}
    [:h3 {:class "panel-title"} title]]
   [:div {:class "panel-body"} msg]])


(defn dispatch-on-press-enter [e d]
  (if (= 13 (.-which e))
    (dispatch d)))


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
            [:i {:class "fa icon-margin-both fa-archive fa-lg fa-6x"}]]]]]
        ]])))


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

(defn login-form []
  (let [username  (subscribe [:credentials :username])
        password  (subscribe [:credentials :password])
        confirm   (subscribe [:credentials :password2])
        message   (subscribe [:credentials :message])
        section   (subscribe [:ui-state :section])
        wip?      (subscribe [:ui-state :wip-login?])
        signup?   (reaction (= :signup @section))
        u-class   (reaction (if (and @signup? (empty? @username)) " has-error"))
        pw-class  (reaction (if (and @signup? (> 5 (count @password))) " has-error"))
        pw2-class (reaction (if (not= @password @confirm) " has-error"))]
    (fn []
      [:div {:class "modal"}
       [:div {:class "modal-dialog"}
        [:div {:class "jumbotron"}
         [:h3 "Welcome!"]
         [:p "Memento is an experimental note-taking application "
          "for thoughts and ideas you may want to revisit."]
         [:p [:a {:class "btn btn-primary"
                  :href  "/about"}
              "Learn more"]]
         ]
        [:div {:class "modal-content"}
         [:div {:class "modal-header"}
          [:h4 {:class "modal-title"} "Login"]]
         [:div {:class "modal-body"}
          (if @message
            [:div {:class (str "col-lg-12 alert " (:type @message))}
             [:p (:text @message)]])
          [:div {:class (str "form-group" @u-class)}
           [:label {:for "inputLogin" :class "col-sm-2 control-label"} "Username"]
           [:div {:class "col-sm-10"}
            [:input {:type         "text"
                     :class        "formControl col-sm-8"
                     :id           "inputLogin"
                     :placeholder  "user name"
                     :on-change    #(dispatch-sync [:state-credentials :username (-> % .-target .-value)])
                     :on-key-press #(dispatch-on-press-enter % [:auth-request @signup?])
                     :value        @username}]]]
          [:div {:class (str "form-group" @pw-class)}
           [:label {:for "inputPassword" :class "col-sm-2 control-label"} "Password"]
           [:div {:class "col-sm-10"}
            [:input {:type         "password"
                     :class        "formControl col-sm-8"
                     :id           "inputPassword"
                     :on-change    #(dispatch-sync [:state-credentials :password (-> % .-target .-value)])
                     :on-key-press #(dispatch-on-press-enter % [:auth-request @signup?])
                     :value        @password}]]]
          (if @signup?
            [:div {:class (str "form-group" @pw2-class)}
             [:label {:for "inputPassword2" :class "col-sm-2 col-lg-2 control-label"} "Confirm:"]
             [:div {:class "col-sm-10 col-lg-10"}
              [:input {:type         "password"
                       :class        "formControl col-sm-8 col-lg-8"
                       :id           "inputPassword2"
                       :on-change    #(dispatch-sync [:state-credentials :password2 (-> % .-target .-value)])
                       :on-key-press #(dispatch-on-press-enter % [:auth-request @signup?])
                       :value        @confirm}]]])

          ]
         [:div {:class "modal-footer"}
          [:button {:type "button" :class "btn btn-primary" :disabled @wip? :on-click #(dispatch [:auth-request @signup?])} "Submit"]]
         ]]]
      )))



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
      [login-form]
      )
    ))

(defn header []
  (let [state (subscribe [:ui-state :section])
        label (case @state
                :record "Record a new thought"
                :remember "Remember"
                "")]
    (if (not-empty label)
      [:h1 {:id "forms"} label])
    ))



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