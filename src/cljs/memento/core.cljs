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
            [markdown.common :as mdcommon]
            [markdown.links :as mdlinks]
            [markdown.lists :as mdlists]
            [markdown.transformers :as transformers]
            [pushy.core :as pushy]
            [ajax.core :refer [GET POST PUT DELETE]]
            [clojure.string :as string])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [memento.misc.cljs-macros :refer [adapt-bootstrap]])
  (:import goog.History))


;;;;------------------------------
;;;; Data and helpers
;;;;------------------------------

(defn paragraph-on-single-line
  "Adds a <p> even when we're at the end of the file and the last line is empty, so that
  we consistently return lines wrapped in paragraph even if it's free-standing text.
  Replaces the default paragraph transformer."
  [text {:keys [eof heading hr code lists blockquote paragraph last-line-empty?] :as state}]
  (cond
    (or heading hr code lists blockquote)
    [text state]

    paragraph
    (if (or eof (empty? (string/trim text)))
      [(str (transformers/paragraph-text last-line-empty? text) "</p>") (assoc state :paragraph false)]
      [(transformers/paragraph-text last-line-empty? text) state])

    last-line-empty?
    [(str "<p>" text) (assoc state :paragraph true :last-line-empty? false)]

    :default
    [text state]))

;; Transformer vector. We are excluding headings, since we use the hash as tags.
(def md-transformers
  [transformers/empty-line
   transformers/codeblock
   transformers/code
   mdcommon/escaped-chars
   mdcommon/inline-code
   transformers/autoemail-transformer
   transformers/autourl-transformer
   mdlinks/link
   mdlinks/reference-link
   mdlists/li
   mdcommon/italics
   mdcommon/em
   mdcommon/strong
   mdcommon/bold
   mdcommon/strikethrough
   transformers/superscript
   transformers/blockquote
   paragraph-on-single-line                                 ; Replaces transformers/paragraph
   transformers/br
   mdcommon/thaw-strings
   ])


(adapt-bootstrap OverlayTrigger)
(adapt-bootstrap Popover)
(adapt-bootstrap Tooltip)
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
    (if (fn? handler)
      (handler route-params)
      (dispatch [:state-ui-section handler]))))

(defn bidi-matcher [s]
  (bidi/match-route routes s))

(def history
  (pushy/pushy set-page! bidi-matcher))


;;;;-------------------------
;;;; Helpers
;;;;-------------------------

(defn add-html-to-thoughts
  "Receives a list of thoughts and converts the markdown to html, adding it
  to the map as a :html attribute"
  [thoughts]
  (map #(assoc % :html (md->html (:thought %) :replacement-transformers md-transformers))
       thoughts))


(defn thread-in-cache?
  "Receives an application state and a thread-id, and returns true if the
  application cache currently contains that thread."
  [app-state thread-id]
  (contains? (get-in app-state [:cache :threads]) (str thread-id)))

;;;;------------------------------
;;;; Handlers
;;;;------------------------------

(defn clear-token-on-unauth
  "Receives an application state and an authorization result. If the status is 401, then it
  dispatches a message to clear the authorization token."
  [result]
  (if (= 401 (:status result))
    (dispatch [:auth-set-token nil])))


(reg-event-db
  :initialize
  (fn [app-state _]
    (merge app-state {:ui-state {:is-busy?      false
                                 :wip-login?    false
                                 :show-thread?  false
                                 :section       :record
                                 :current-query ""
                                 :results-page  0
                                 :memories      {:pages 0}
                                 :is-searching? false}
                      :cache    {}
                      :note     {:edit-memory nil}
                      })))


(reg-event-db
  :auth-request
  (fn [app-state [_ signup?]]
    (let [url       (if signup? "signup" "login")
          ;; Should probalby centralize password validation, so we can use the same function
          ;; both here and when the UI is being updated
          is-valid? (or (not signup?)
                        (= (get-in app-state [:credentials :password])
                           (get-in app-state [:credentials :password2])))]
      (if is-valid?
        (POST (str "/api/auth/" url) {:params        (:credentials app-state)
                                      :handler       #(dispatch [:auth-set-token (:token %)])
                                      :error-handler #(dispatch [:auth-request-error %])}))
      )
    (assoc-in app-state [:ui-state :wip-login?] true)
    ))

(reg-event-db
  :auth-set-token
  (fn [app-state [_ token]]
    (if (not-empty token)
      (dispatch [:state-message ""]))
    (cookies/set! :token token)
    (if (empty? token)
      (set-page! "/login")
      (set-page! (bidi-matcher (-> js/window .-location .-pathname))))
    (-> app-state
        (assoc-in [:credentials :token] token)
        (assoc-in [:ui-state :wip-login?] false)
        (assoc-in [:credentials :password] nil)
        (assoc-in [:credentials :password2] nil))))


(reg-event-db
  :auth-request-error
  (fn [app-state [_ result]]
    (.log js/console (str result))
    (let [status    (:status result)
          is-unauth (or (= 401 status) (= 409 status))
          message   (if is-unauth "Invalid username/password" (:status-text result))
          msg-type  (if is-unauth "alert-danger" "alert-warning")]
      (-> app-state
          (assoc-in [:ui-state :wip-login?] false)
          (assoc-in [:credentials :message] {:text message :type msg-type})
          (assoc-in [:credentials :token] nil)
          (assoc-in [:credentials :password] nil)
          (assoc-in [:credentials :password2] nil)))
    ))

(reg-event-db
  :auth-validate
  (fn [app-state _]
    (when-let [token (get-in app-state [:credentials :token])]
      (GET "/api/auth/validate" {:headers       {:authorization (str "Token " token)}
                                 :handler       #(dispatch [:auth-set-token (:token %)])
                                 :error-handler #(dispatch [:auth-request-error %])}))
    app-state
    ))

(reg-event-db
  :memories-load
  (fn [app-state [_ page-index]]
    (let [q      (get-in app-state [:ui-state :current-query])
          last-q (get-in app-state [:search-state :query])
          list   (if (= q last-q)
                   (get-in app-state [:search-state :list])
                   [])
          p      (or page-index (get-in app-state [:ui-state :results-page]))]
      (if (or (not= q last-q)
              (> p (or (get-in app-state [:search-state :page-index]) -1)))
        (do
          (GET "/api/search" {:params        {:q q :page p}
                              :headers       {:authorization (str "Token " (get-in app-state [:credentials :token]))}
                              :handler       #(dispatch [:memories-load-success %])
                              :error-handler #(dispatch [:memories-load-error %])
                              })
          (-> app-state
              (assoc-in [:ui-state :is-searching?] true)
              (assoc :search-state {:query       q
                                    :page-index  p
                                    :list        list
                                    :last-result (get-in app-state [:search-state :last-result])})
              ))
        app-state
        ))))

(reg-event-db
  :memories-load-next
  (fn [app-state [_]]
    (dispatch [:memories-load (inc (get-in app-state [:search-state :page-index]))])
    app-state))

(reg-event-db
  :memories-load-success
  (fn [app-state [_ memories]]
    (-> app-state
        (assoc-in [:search-state :list] (concat (get-in app-state [:search-state :list]) (add-html-to-thoughts (:results memories))))
        (assoc-in [:search-state :last-result] memories)
        (assoc-in [:ui-state :is-searching?] false))
    ))

(reg-event-db
  :memories-load-error
  (fn [app-state [_ result]]
    (dispatch [:state-message (str "Error remembering: " result) "alert-danger"])
    (clear-token-on-unauth result)
    app-state
    ))


(reg-event-db
  :memory-edit-set
  (fn [app-state [_ thought]]
    (if (empty? thought)
      (dispatch [:state-note :edit-note nil]))
    (assoc-in app-state [:note :edit-memory] thought)
    ))

(reg-event-db
  :memory-edit-save
  (fn [app-state _]
    (let [note   (get-in app-state [:note :edit-note])
          memory (get-in app-state [:note :edit-memory])
          url    (str "/api/thoughts/" (:id memory))]
      (PUT url {:params        {:thought note}
                :headers       {:authorization (str "Token " (get-in app-state [:credentials :token]))}
                :handler       #(dispatch [:memory-edit-save-success memory note])
                :error-handler #(dispatch [:memory-edit-save-error memory %])}))
    (assoc-in app-state [:ui-state :is-busy?] true)))


(reg-event-db
  :memory-edit-save-success
  (fn [app-state [_ memory msg]]
    (let [thread-id (:root_id memory)]
      (dispatch [:state-message (str "Updated memory to: " msg) "alert-success"])
      (if (= :remember (get-in app-state [:ui-state :section])) ; Just in case we allow editing from elsewhere...
        (dispatch [:memories-load]))
      (when (thread-in-cache? app-state thread-id)
        (dispatch [:thread-load thread-id]))
      (-> app-state
          (assoc-in [:ui-state :is-busy?] false)
          (assoc-in [:note :edit-memory] nil)
          (assoc-in [:note :edit-note] "")
          (assoc-in [:note :focus] nil)
          (assoc :search-state nil)
          ))))

(reg-event-db
  :memory-edit-save-error
  (fn [app-state [_ memory result]]
    (dispatch [:state-message (str "Error editing memory: " result) "alert-danger"])
    (clear-token-on-unauth result)
    (assoc-in app-state [:ui-state :is-busy?] false)))

(reg-event-db
  :memory-forget
  (fn [app-state [_ memory]]
    (let [url (str "/api/thoughts/" (:id memory))]
      (DELETE url {:headers       {:authorization (str "Token " (get-in app-state [:credentials :token]))}
                   :handler       #(dispatch [:memory-forget-success memory %])
                   :error-handler #(dispatch [:memory-forget-error %])}))
    app-state))

(reg-event-db
  :memory-forget-success
  (fn [app-state [_ memory msg]]
    (let [thread-id (:root_id memory)]
      (dispatch [:state-message (str "Thought forgotten") "alert-success"])
      (if (= :remember (get-in app-state [:ui-state :section])) ; Just in case we allow editing from elsewhere...
        (dispatch [:memories-load]))
      (when (thread-in-cache? app-state thread-id)
        (dispatch [:thread-load thread-id]))
      (-> app-state
          (assoc-in [:ui-state :is-busy?] false)
          (assoc-in [:note :edit-memory] nil)
          (assoc-in [:note :edit-note] "")
          (assoc-in [:note :focus] nil)
          (assoc :search-state nil)
          )))
  )

(reg-event-db
  :memory-forget-error
  (fn [app-state [_ result]]
    (.log js/console "Forget error" result)
    (dispatch [:state-message (str "Error forgetting: " result) "alert-danger"])
    (clear-token-on-unauth result)
    (assoc-in app-state [:ui-state :is-busy?] false)))

(reg-event-db
  :memory-save
  (fn [app-state _]
    (let [note (get-in app-state [:note :current-note])]
      (POST "/api/thoughts" {:params        {:thought note :refine_id (get-in app-state [:note :focus :id])}
                             :headers       {:authorization (str "Token " (get-in app-state [:credentials :token]))}
                             :handler       #(dispatch [:memory-save-success % note])
                             :error-handler #(dispatch [:memory-save-error %])}))
    (assoc-in app-state [:ui-state :is-busy?] true)))

(reg-event-db
  :memory-save-success
  (fn [app-state [_ result msg]]
    (dispatch [:state-message (str "Saved: " msg) "alert-success"])
    (let [thread-id (str (:root_id result))]
      (when (thread-in-cache? app-state thread-id)
        (dispatch [:thread-load thread-id])))
    (-> app-state
        (assoc-in [:ui-state :is-busy?] false)
        (assoc-in [:note :current-note] "")
        (assoc-in [:ui-state :show-thread-id] nil)
        (assoc-in [:ui-state :show-thread?] false)
        (assoc-in [:note :focus] nil)
        (assoc :search-state nil))))

(reg-event-db
  :memory-save-error
  (fn [app-state [_ result]]
    (dispatch [:state-message (str "Error saving note: " result) "alert-danger"])
    (clear-token-on-unauth result)
    (assoc-in app-state [:ui-state :is-busy?] false)
    ))

(reg-event-db
  ; Separate handler from :thread-load so that we can choose when to display a thread and when to load it.
  :thread-display
  (fn [app-state [_ root-id]]
    (when (empty? (get-in app-state [:cache :threads root-id]))
      (dispatch [:thread-load root-id]))
    (-> app-state
        (assoc-in [:ui-state :show-thread?] true)
        (assoc-in [:ui-state :show-thread-id] root-id))))

(reg-event-db
  :thread-load
  (fn [app-state [_ root-id]]
    (let [url (str "/api/threads/" root-id)]
      (GET url {:headers       {:authorization (str "Token " (get-in app-state [:credentials :token]))}
                :handler       #(dispatch [:thread-load-success %])
                :error-handler #(dispatch [:thread-load-error %])}
           ))
    app-state))

(reg-event-db
  :thread-load-error
  (fn [app-state [_ result]]
    ; Not sure if we actually know which thread failed loading. Probably not if the call failed altogether.
    ; If we did, we could just assoc the thread to nil.
    (dispatch [:state-message (str "Error loading thread: " result) "alert-danger"])
    app-state))

(reg-event-db
  :thread-load-success
  (fn [app-state [_ {:keys [id results] :as result}]]
    (dispatch [:state-ui-section :remember])
    (assoc-in app-state [:cache :threads id] (add-html-to-thoughts results))))


(reg-event-db
  :refine
  (fn [app-state [_ thought]]
    (dispatch [:state-browser-token :record])
    (assoc-in app-state [:note :focus] thought)))


;; Handler for changing the browser token from a keyword, so that
;; :record leads to /record. The handler is expected to apply any
;; necessary changes to the ui state, or dispatch the relevant
;; events.
(reg-event-db
  :state-browser-token
  (fn [app-state [_ token-key]]
    (pushy/set-token! history (bidi/path-for routes token-key))
    app-state))

(reg-event-db
  :state-credentials
  (fn [app-state [_ k v]]
    (assoc-in app-state [:credentials k] v)))

(reg-event-db
  :state-ui-section
  (fn [app-state [_ section]]
    (if (= :remember section)
      (dispatch [:memories-load]))
    ; Do not associate nil sections
    (if (some? section)
      (assoc-in app-state [:ui-state :section] section)
      app-state)))

(reg-event-db
  :state-message
  (fn [app-state [_ msg class]]
    (let [message {:text msg :class class}]
      ; TODO: Consider changing this for a keyword
      (if (= class "alert-success")
        (js/setTimeout #(dispatch [:state-message-if-same message nil]) 3000))
      (assoc-in app-state [:ui-state :last-message] message))))

(reg-event-db
  :state-message-if-same
  (fn [app-state [_ compare-msg new-msg]]
    (if (= compare-msg (get-in app-state [:ui-state :last-message]))
      (assoc-in app-state [:ui-state :last-message] new-msg)
      app-state
      )))

(reg-event-db
  :state-note
  (fn [app-state [_ note-id note]]
    (assoc-in app-state [:note note-id] note)))

(reg-event-db
  :state-current-query
  (fn [app-state [_ q]]
    (dispatch [:memories-load 0])
    (assoc-in app-state [:ui-state :current-query] q)))

(reg-event-db
  :state-show-thread
  (fn [app-state [_ show?]]
    (when-not show? (dispatch [:state-browser-token :remember]))
    (assoc-in app-state [:ui-state :show-thread?] show?)))



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
    [:li {:class class} [:a {:href (bidi/path-for routes section)}
                         label
                         (if @is-current?
                           [:span {:class "sr-only"} "(current)"])]]))


(defn navbar []
  (let [token (subscribe [:credentials :token])]
    (fn []
      [:nav {:class "navbar navbar-default navbar-fixed-top"}
       [:div {:class "container-fluid"}
        [:div {:class "navbar-header"}
         [:a {:class "navbar-brand"} "Memento"]]
        [:div {:class "collapse navbar-collapse" :id "navbar-items"}
         [:ul {:class "nav navbar-nav"}
          (if (nil? @token)
            [:ul {:class "nav navbar-nav"}
             [navbar-item "Login" :login]
             [navbar-item "Sign up" :signup]]
            [:ul {:class "nav navbar-nav"}
             [navbar-item "Record" :record]
             [navbar-item "Remember" :remember]])
          ]]]])))


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
        [:div {:class "panel-heading"} "Elaborating... " [:i [:small "(from " (:created @focus) ")"]]
         [:button {:type "button" :class "close" :aria-hidden "true" :on-click #(dispatch [:refine nil])} "×"]]
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

(defn write-section []
  (let [note        (subscribe [:note :current-note])
        is-busy?    (subscribe [:ui-state :is-busy?])
        focus       (subscribe [:note :focus])
        is-focused? (reaction (not-empty @focus))]
    (fn []
      [:fielset
       [:div {:class "form-horizontal"}
        [thought-edit-box :current-note]
        [:div {:class "form-group"}
         [:div {:class "col-sm-12" :style {:text-align "right"}}
          [:button {:type     "submit"
                    :disabled (or @is-busy? (empty? @note))
                    :class    "btn btn-primary"
                    :on-click #(dispatch [:memory-save])}
           (if @is-focused? "Elaborate" "Record")]
          ]]
        ]]
      )))

(defn panel [title msg class]
  [:div {:class (str "panel " class)}
   [:div {:class "panel-heading"}
    [:h3 {:class "panel-title"} title]
    ]
   [:div {:class "panel-body"} msg]
   ])


(defn dispatch-on-press-enter [e d]
  (if (= 13 (.-which e))
    (dispatch d)))


(defn memory-query []
  (let [query (subscribe [:ui-state :current-query])]
    (fn []
      [:div {:class "form-horizontal"}
       [:div {:class "form-group"}
        [:label {:for "input-search" :class "col-md-1 control-label"} "Search:"]
        [:div {:class "col-md-10"}
         [initial-focus-wrapper
          [:input {:type      "text"
                   :class     "form-control"
                   :id        "input-search"
                   :value     @query
                   :on-change #(dispatch-sync [:state-current-query (-> % .-target .-value)])}]]
         ]]])))


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
        [:span {:dangerouslySetInnerHTML {:__html (:html memory)}}]
        ]
       [:div
        [:div {:class "col-sm-4 show-on-hover"}
         [:i [:small (:created memory)]]
         (when (= :open (:status memory))
           [OverlayTrigger
            {:placement :top
             :overlay   tooltip}
            [:span {:class    "btn btn-danger btn-xs icon-margin-left"
                    :on-click #(dispatch [:memory-forget memory])}
             [:i {:class "fa fa-remove"}]]
            ])]
        [:div {:class "col-sm-6 col-sm-offset-2" :style {:text-align "right"}}
         (when (= :open (:status memory))
           [:a {:class    "btn btn-primary btn-xs"
                :on-click #(do
                            (dispatch [:state-note :edit-note (:thought memory)])
                            (dispatch [:memory-edit-set memory]))}
            [:i {:class "fa fa-file-text icon-margin-both"}] "Edit"])
         (if (and show-thread-btn? (:root_id memory))
           [:a {:class "btn btn-primary btn-xs"
                :href  (str "/thread/" (:root_id memory))}
            [:i {:class "fa fa-list-ul icon-margin-both"}] "Thread"])
         [:a {:class    "btn btn-primary btn-xs"
              :on-click #(do
                          (.scrollIntoView top-div-target)
                          (dispatch [:refine memory]))}
          [:i {:class "fa fa-pencil icon-margin-both"}] "Elaborate"]]
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
        ; May want to add a style to show the Cancel button only on mobile, as the same functionality can
        ; easily be triggered on desktop by pressing Esc
        [:button {:type     "reset"
                  :class    "btn btn-default"
                  :on-click #(dispatch [:memory-edit-set nil])} "Cancel"]
        ]])))


(defn memory-thread []
  (let [show?     (subscribe [:ui-state :show-thread?])
        thread-id (subscribe [:ui-state :show-thread-id])
        ; I subscribe to the whole thread cache because I can't just subscribe to [:threads @thread-id],
        ; as it'd only be evaluated once. I tried to do a reaction with the path, then subscribe
        ; to the @path... but the subscription is not refreshed when the @path changes.
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
               "Memories")
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
        pw2-class (reaction (if (not= @password @confirm) " has-error"))
        ]
    (fn []
      [:div {:class "modal"}
       [:div {:class "modal-dialog"}
        [:div {:class "modal-content"}
         [:div {:class "modal-header"}
          [:h4 {:clss "modal-title"} "Login"]]
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
    (condp = @section
      :record [write-section]
      :remember [memory-list]
      [login-form]
      )
    ))

(defn header []
  (let [state  (subscribe [:ui-state :section])
        header (condp = @state
                 :record "Record a new thought"
                 :remember "Remember"
                 "")]
    (if (not-empty header)
      [:h1 {:id "forms"} header])
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
  (pushy/start! history)
  (dispatch-sync [:initialize])
  (dispatch-sync [:auth-set-token (cookies/get :token nil)])
  (dispatch-sync [:auth-validate])
  (add-on-appear-handler "#load-trigger" #(dispatch [:memories-load-next]))
  (mount-components))