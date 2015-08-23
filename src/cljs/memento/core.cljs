(ns memento.core
  (:require [ajax.core :refer [GET POST PUT]]
            [clojure.string :refer [trim split]]
            [cljsjs.react-bootstrap]
            [reagent.cookies :as cookies]
            [reagent.core :as reagent :refer [atom]]
            [re-frame.core :refer [dispatch register-sub register-handler subscribe dispatch-sync]]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [jayq.core :refer [$]]
            [markdown.core :refer [md->html]]
            [markdown.transformers :as transformers]
            [ajax.core :refer [GET POST PUT]]
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
   transformers/escaped-chars
   transformers/inline-code
   transformers/autoemail-transformer
   transformers/autourl-transformer
   transformers/link
   transformers/reference-link
   transformers/li
   transformers/italics
   transformers/em
   transformers/strong
   transformers/bold
   transformers/strikethrough
   transformers/superscript
   transformers/blockquote
   paragraph-on-single-line                                           ; Replaces transformers/paragraph
   transformers/br])


#_(adapt-bootstrap Pagination)
(adapt-bootstrap OverlayTrigger)
(adapt-bootstrap Popover)
(adapt-bootstrap Tooltip)
(def Pagination (reagent/adapt-react-class js/ReactBootstrap.Pagination))
(def Modal (reagent/adapt-react-class js/ReactBootstrap.Modal))
(def ModalBody (reagent/adapt-react-class js/ReactBootstrap.ModalBody))
(def ModalFooter (reagent/adapt-react-class js/ReactBootstrap.ModalFooter))
(def ModalHeader (reagent/adapt-react-class js/ReactBootstrap.ModalHeader))
(def ModalTitle (reagent/adapt-react-class js/ReactBootstrap.ModalTitle))
(def ModalTrigger (reagent/adapt-react-class js/ReactBootstrap.ModalTrigger))


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
  (reaction (get-in @db [sid element-id])))

(register-sub :note general-query)
(register-sub :ui-state general-query)
(register-sub :credentials general-query)



;;;;------------------------------
;;;; Handlers
;;;;------------------------------

(defn clear-token-on-unauth
  "Receives an application state and an authorization result. If the status is 401, then it
  dispatches a message to clear the authorization token."
  [result]
  (if (= 401 (:status result))
    (dispatch [:auth-set-token nil])))


(register-handler
  :initialize
  (fn [app-state _]
    (dispatch [:auth-set-token (cookies/get :token nil)])
    (merge app-state {:ui-state {:is-busy?      false
                                 :wip-login?    false
                                 :show-thread?  false
                                 :section       :login
                                 :current-query ""
                                 :results-page  0
                                 :memories      {:pages 0}
                                 :is-searching? false}
                      :note     {:edit-memory nil}
                      })))

(register-handler
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

(register-handler
  :auth-set-token
  (fn [app-state [_ token]]
    (if (not-empty token)
      (dispatch [:state-message ""]))
    (cookies/set! :token token)
    (-> app-state
        (assoc-in [:credentials :token] token)
        (assoc-in [:ui-state :section] (if (empty? token) :login :record))
        (assoc-in [:ui-state :wip-login?] false)
        (assoc-in [:credentials :password] nil)
        (assoc-in [:credentials :password2] nil))))

(register-handler
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


(register-handler
  :memories-load
  (fn [app-state [_ page-index]]
    (GET "/api/memory/search" {:params        {:q    (get-in app-state [:ui-state :current-query])
                                               :page (or page-index (get-in app-state [:ui-state :results-page]))}
                               :headers       {:authorization (str "Token " (get-in app-state [:credentials :token]))}
                               :handler       #(dispatch [:memories-load-success %])
                               :error-handler #(dispatch [:memories-load-error %])
                               })
    (assoc-in app-state [:ui-state :is-searching?] true)
    ))

(register-handler
  :memories-load-success
  (fn [app-state [_ memories]]
    (.scrollIntoView top-div-target)
    (-> app-state
        (assoc-in [:ui-state :results-page] (:current-page memories))
        (assoc-in [:ui-state :memories] memories)
        (assoc-in [:ui-state :is-searching?] false))
    ))

(register-handler
  :memories-load-error
  (fn [app-state [_ result]]
    (dispatch [:state-message (str "Error remembering: " result) "alert-danger"])
    (clear-token-on-unauth result)
    app-state
    ))

(register-handler
  :memories-page
  (fn [app-state [_ i]]
    (let [max          (dec (get-in app-state [:ui-state :memories :pages]))
          idx          (Math/max 0 (Math/min max i))
          current-page (get-in app-state [:ui-state :memories :current-page])]
      (if (not= current-page idx)
        (dispatch [:memories-load idx]))
      (assoc-in app-state [:ui-state :results-page] idx))
    ))

(register-handler
  :memory-edit-set
  (fn [app-state [_ thought]]
    (if (empty? thought)
      (dispatch [:state-note :edit-note nil]))
    (assoc-in app-state [:note :edit-memory] thought)
    ))


(register-handler
  :memory-edit-save
  (fn [app-state _]
    (let [note   (get-in app-state [:note :edit-note])
          memory (get-in app-state [:note :edit-memory])
          url    (str "/api/memory/" (:id memory) "/thought")]
      (PUT url {:params        {:thought note :refine_id (get-in app-state [:note :focus :id])}
                :headers       {:authorization (str "Token " (get-in app-state [:credentials :token]))}
                :handler       #(dispatch [:memory-edit-save-success note])
                :error-handler #(dispatch [:memory-edit-save-error %])})
      )
    (assoc-in app-state [:ui-state :is-busy?] true)
    ))


(register-handler
  :memory-edit-save-success
  (fn [app-state [_ msg]]
    (dispatch [:state-message (str "Updated memory to: " msg) "alert-success"])
    (if (= :remember (get-in app-state [:ui-state :section]))         ; Just in case we allow editing from elsewhere...
      (dispatch [:memories-load]))
    (-> app-state
        (assoc-in [:ui-state :is-busy?] false)
        (assoc-in [:note :edit-memory] nil)
        (assoc-in [:note :edit-note] "")
        (assoc-in [:note :focus] nil)
        )))


(register-handler
  :memory-edit-save-error
  (fn [app-state [_ result]]
    (dispatch [:state-message (str "Error editing note: " result) "alert-danger"])
    (clear-token-on-unauth result)
    (assoc-in app-state [:ui-state :is-busy?] false)
    ))


(register-handler
  :memory-save
  (fn [app-state _]
    (let [note (get-in app-state [:note :current-note])]
      (POST "/api/memory" {:params        {:thought note :refine_id (get-in app-state [:note :focus :id])}
                           :headers       {:authorization (str "Token " (get-in app-state [:credentials :token]))}
                           :handler       #(dispatch [:memory-save-success note])
                           :error-handler #(dispatch [:memory-save-error %])}))
    (assoc-in app-state [:ui-state :is-busy?] true)
    ))

(register-handler
  :memory-save-success
  (fn [app-state [_ msg]]
    (dispatch [:state-message (str "Saved: " msg) "alert-success"])
    (-> app-state
        (assoc-in [:ui-state :is-busy?] false)
        (assoc-in [:note :current-note] "")
        (assoc-in [:note :focus] nil)
        )))

(register-handler
  :memory-save-error
  (fn [app-state [_ result]]
    (dispatch [:state-message (str "Error saving note: " result) "alert-danger"])
    (clear-token-on-unauth result)
    (assoc-in app-state [:ui-state :is-busy?] false)
    ))




(register-handler
  :thread-load
  (fn [app-state [_ thought]]
    (let [url (str "/api/memory/" (:root_id thought) "/thread")]
      (GET url {:headers       {:authorization (str "Token " (get-in app-state [:credentials :token]))}
                :handler       #(dispatch [:thread-load-success %])
                :error-handler #(dispatch [:thread-load-error %])}
           ))
    app-state))

(register-handler
  :thread-load-error
  (fn [app-state [_ result]]
    (dispatch [:state-message (str "Error loading thread: " result) "alert-danger"])
    app-state))

(register-handler
  :thread-load-success
  (fn [app-state [_ result]]
    (-> app-state
        (assoc-in [:ui-state :show-thread?] true)
        (assoc-in [:note :thread] (:results result)))
    ))


(register-handler
  :refine
  (fn [app-state [_ thought]]
    (-> app-state
        (assoc-in [:note :focus] thought)
        (assoc-in [:ui-state :section] :record))
    ))

(register-handler
  :state-credentials
  (fn [app-state [_ k v]]
    (assoc-in app-state [:credentials k] v)))


(register-handler
  :state-ui-section
  (fn [app-state [_ section]]
    (if (= :remember section)
      (dispatch [:memories-load]))
    (assoc-in app-state [:ui-state :section] section)))

(register-handler
  :state-message
  (fn [app-state [_ msg class]]
    (let [message {:text msg :class class}]
      ; TODO: Consider changing this for a keyword
      (if (= class "alert-success")
        (js/setTimeout #(dispatch [:state-message-if-same message nil]) 3000))
      (assoc-in app-state [:ui-state :last-message] message))))

(register-handler
  :state-message-if-same
  (fn [app-state [_ compare-msg new-msg]]
    (if (= compare-msg (get-in app-state [:ui-state :last-message]))
      (assoc-in app-state [:ui-state :last-message] new-msg)
      app-state
      )))

(register-handler
  :state-note
  (fn [app-state [_ note-id note]]
    (assoc-in app-state [:note note-id] note)))

(register-handler
  :state-current-query
  (fn [app-state [_ q]]
    (dispatch [:memories-load 0])
    (assoc-in app-state [:ui-state :current-query] q)))

(register-handler
  :state-show-thread
  (fn [app-state [_ state]]
    (assoc-in app-state [:ui-state :show-thread?] state)))




;;;;------------------------------
;;;; Components
;;;;------------------------------


(def initial-focus-wrapper
  (with-meta identity
             {:component-did-mount #(.focus (reagent/dom-node %))}))

(defn navbar-item
  "Renders a navbar item. Having each navbar item have its own subscription will probably
  have a bit of overhead, but I don't imagine it'll be anything major since we won't have
  more than a couple of them."
  [name section]
  (let [current     (subscribe [:ui-state :section])
        is-current? (reaction (= section @current))
        class       (when @is-current? "active")]
    [:li {:class class} [:a {:on-click #(dispatch [:state-ui-section section])} name
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
          ]]
        ]]
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
        [:div {:class "panel-heading"} "Refining... " [:i [:small "(from " (:created @focus) ")"]]
         [:button {:type "button" :class "close" :aria-hidden "true" :on-click #(dispatch [:refine nil])} "×"]]
        [:div {:class "panel-body"}
         [:p {:dangerouslySetInnerHTML {:__html (md->html (:thought @focus) :replacement-transformers md-transformers)}}]
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
  (let [note     (subscribe [:note :current-note])
        is-busy? (subscribe [:ui-state :is-busy?])]
    (fn []
      [:fielset
       [:div {:class "form-horizontal"}
        [thought-edit-box :current-note]
        [:div {:class "form-group"}
         [:div {:class "col-sm-12"}
          [:button {:type     "reset"
                    :class    "btn btn-default"
                    :on-click #(dispatch [:state-note :current-note ""])} "Clear"]
          [:button {:type     "submit"
                    :disabled (or @is-busy? (empty? @note))
                    :class    "btn btn-primary"
                    :on-click #(dispatch [:memory-save])} "Submit"]
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

(defn memory-pager []
  (let [memories (subscribe [:ui-state :memories])
        pages    (reaction (:pages @memories))
        current  (subscribe [:ui-state :results-page])
        max-btn  (reaction (Math/min @pages 8))]
    (fn []
      (if (> @pages 1)
        [:div {:style {:text-align "center"}}
         [Pagination {:items      @pages
                      :maxButtons @max-btn
                      :prev       true
                      :next       true
                      :first      (>= @current @max-btn)
                      :last       (< @current (- @pages @max-btn))
                      :activePage (inc @current)
                      :onSelect   #(dispatch [:memories-page (dec (aget %2 "eventKey"))])
                      }]]))))

(defn list-memories [results show-thread-btn?]
  (for [memory results]
    ^{:key (:id memory)}
    [:div {:class "col-sm-12 thought"}
     [:div {:class "memory col-sm-12"}
      [:span {:dangerouslySetInnerHTML {:__html (md->html (:thought memory) :replacement-transformers md-transformers)}}]
      ]
     [:div
      [:div {:class "col-sm-6"}
       (if (= :open (:status memory))
         [:a {:class    "btn btn-primary btn-xs"
              :on-click #(do
                          (dispatch [:state-note :edit-note (:thought memory)])
                          (dispatch [:memory-edit-set memory]))}
          "Edit" [:i {:class "fa fa-pencil fa-space"}]])
       [:a {:class    "btn btn-primary btn-xs"
            :on-click #(do
                        (.scrollIntoView top-div-target)
                        (dispatch [:refine memory]))}
        "Refine" [:i {:class "fa fa-comment fa-space"}]]
       (if (and show-thread-btn? (:root_id memory))
         [:a {:class    "btn btn-primary btn-xs"
              :on-click #(dispatch [:thread-load memory])}
          "Thread" [:i {:class "fa fa-file-text fa-space"}]])

       ]
      [:div {:class "col-sm-4 col-sm-offset-2" :style {:text-align "right"}}
       [:i [:small (:created memory)]]
       ]]]))


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
        [:button {:type     "reset"
                  :class    "btn btn-default"
                  :on-click #(dispatch [:memory-edit-set nil])} "Discard"]
        [:button {:type     "submit"
                  :class    "btn btn-primary"
                  :disabled (or @is-busy? (empty? @note))
                  :on-click #(dispatch [:memory-edit-save])} "Save"]
        ]])))


(defn memory-thread []
  (let [show?  (subscribe [:ui-state :show-thread?])
        thread (subscribe [:note :thread])]
    (fn []
      [Modal {:show @show? :onHide #(dispatch [:state-show-thread false])}
       [ModalBody
        (list-memories @thread false)]
       [ModalFooter
        [:button {:type     "reset"
                  :class    "btn btn-default"
                  :on-click #(dispatch [:state-show-thread false])} "Close"]]])))


(defn memory-results []
  (let [busy?    (subscribe [:ui-state :is-searching?])
        memories (subscribe [:ui-state :memories])
        results  (reaction (:results @memories))]
    (fn []
      [panel (if @busy?
               [:span "Loading..." [:i {:class "fa fa-spin fa-space fa-circle-o-notch"}]]
               "Memories")
       [:span
        (if (empty? @results)
          [:p "Nothing."]
          (list-memories @results true))
        [memory-pager]
        ]
       "panel-primary"]
      )))

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
      (dispatch [:state-ui-section :login]))
    (condp = @section
      :record [write-section]
      :remember [memory-list]
      [login-form]
      )
    )
  )

(defn header []
  (let [state  (subscribe [:ui-state :section])
        header (condp = @state
                 :record "Make a new memory"
                 :remember "Remember"
                 "")]
    (if (not-empty header)
      [:h1 {:id "forms"} header])
    ))




;; -------------------------
;; History
;; must be called after routes have been defined
;; TODO: Figure out how to do that with re-frame
(defn hook-browser-navigation! []
  #_(doto (History.)
      (events/listen
        EventType/NAVIGATE
        (fn [event]
          (secretary/dispatch! (.-token event))))
      (.setEnabled true)))

;; -------------------------
;; Initialize app

(defn mount-components []
  (reagent/render-component [navbar] (.getElementById js/document "navbar"))
  (reagent/render-component [content-section] (.getElementById js/document "content-section"))
  (reagent/render-component [alert] (.getElementById js/document "alert"))
  (reagent/render-component [header] (.getElementById js/document "header")))

(defn init! []
  (dispatch-sync [:initialize])
  (hook-browser-navigation!)
  (mount-components))