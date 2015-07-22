(ns memento.core
  (:require [ajax.core :refer [GET POST PUT]]
            [clojure.string :refer [trim split]]
            [cljsjs.react-bootstrap]
            [reagent.cookies :as cookies]
            [reagent.core :as reagent :refer [atom]]
            [re-frame.core :refer [dispatch register-sub register-handler subscribe dispatch-sync]]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [markdown.core :refer [md->html]]
            [markdown.transformers :as transformers]
            [ajax.core :refer [GET POST]])
  (:require-macros [reagent.ratom :refer [reaction]])
  (:import goog.History))


;;;;------------------------------
;;;; Data and helpers
;;;;------------------------------

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
   transformers/hr
   transformers/li
   transformers/italics
   transformers/em
   transformers/strong
   transformers/bold
   transformers/strikethrough
   transformers/superscript
   transformers/blockquote
   transformers/paragraph
   transformers/br])


(def Pagination (reagent/adapt-react-class js/ReactBootstrap.Pagination))


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
    (dispatch [:set-token nil])))


(register-handler
  :initialize
  (fn [app-state _]
    (dispatch [:set-token (cookies/get :token nil)])
    (merge app-state {:ui-state {:is-busy?      false
                                 :wip-login?    false
                                 :section       :login
                                 :current-query ""
                                 :results-page  0
                                 :memories      {:pages 0}
                                 :is-searching? false}
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
                                      :handler       #(dispatch [:set-token (:token %)])
                                      :error-handler #(dispatch [:login-error %])}))
      )
    (assoc-in app-state [:ui-state :wip-login?] true)
    ))

(register-handler
  :set-token
  (fn [app-state [_ token]]
    (if (not-empty token)
      (dispatch [:set-message ""]))
    (cookies/set! :token token)
    (-> app-state
        (assoc-in [:credentials :token] token)
        (assoc-in [:ui-state :section] (if (empty? token) :login :write))
        (assoc-in [:ui-state :wip-login?] false)
        (assoc-in [:credentials :password] nil)
        (assoc-in [:credentials :password2] nil))))

(register-handler
  :login-error
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
  :update-credentials
  (fn [app-state [_ k v]]
    (assoc-in app-state [:credentials k] v)))

(register-handler
  :set-ui-section
  (fn [app-state [_ section]]
    (if (= :remember section)
      (dispatch [:load-memories]))
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
  :update-query
  (fn [app-state [_ q]]
    (dispatch [:load-memories 0])
    (assoc-in app-state [:ui-state :current-query] q)))

(register-handler
  :load-memories
  (fn [app-state [_ page-index]]
    (GET "/api/memory/search" {:params        {:q    (get-in app-state [:ui-state :current-query])
                                               :page (or page-index (get-in app-state [:ui-state :results-page]))}
                               :headers       {:authorization (str "Token " (get-in app-state [:credentials :token]))}
                               :handler       #(dispatch [:load-memories-success %])
                               :error-handler #(dispatch [:load-memories-error %])
                               })
    (assoc-in app-state [:ui-state :is-searching?] true)
    ))

(register-handler
  :load-memories-success
  (fn [app-state [_ memories]]
    (-> app-state
        (assoc-in [:ui-state :results-page] (:current-page memories))
        (assoc-in [:ui-state :memories] memories)
        (assoc-in [:ui-state :is-searching?] false))
    ))


(register-handler
  :load-memories-error
  (fn [app-state [_ result]]
    (dispatch [:set-message (str "Error remembering: " result) "alert-danger"])
    (clear-token-on-unauth result)
    app-state
    ))


(register-handler
  :page-memories
  (fn [app-state [_ i]]
    (let [max          (dec (get-in app-state [:ui-state :memories :pages]))
          idx          (Math/max 0 (Math/min max i))
          current-page (get-in app-state [:ui-state :memories :current-page])]
      (if (not= current-page idx)
        (dispatch [:load-memories idx]))
      (assoc-in app-state [:ui-state :results-page] idx))
    ))


(register-handler
  :save-note
  (fn [app-state _]
    (let [note (get-in app-state [:note :current-note])]
      (POST "/api/memory" {:params        {:thought note}
                           :headers       {:authorization (str "Token " (get-in app-state [:credentials :token]))}
                           :handler       #(dispatch [:save-note-success note])
                           :error-handler #(dispatch [:save-note-error %])}))
    (assoc-in app-state [:ui-state :is-busy?] true)
    ))

(register-handler
  :save-note-success
  (fn [app-state [_ msg]]
    (dispatch [:set-message (str "Saved: " msg) "alert-success"])
    (-> app-state
        (assoc-in [:ui-state :is-busy?] false)
        (assoc-in [:note :current-note] "")
        )))

(register-handler
  :save-note-error
  (fn [app-state [_ result]]
    (dispatch [:set-message (str "Error saving note: " result) "alert-danger"])
    (clear-token-on-unauth result)
    (assoc-in app-state [:ui-state :is-busy?] false)
    ))


;;;;------------------------------
;;;; Components
;;;;------------------------------


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
             [navbar-item "Record" :write]
             [navbar-item "Remember" :remember]])
          ]]
        ]]
      )))


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
  (let [note     (subscribe [:note :current-note])
        is-busy? (subscribe [:ui-state :is-busy?])]
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
        [:label {:for "input-search" :class "col-lg-2 control-label"} "Search:"]
        [:div {:class "col-lg-9"}
         [:input {:type      "text"
                  :class     "form-control"
                  :id        "input-search"
                  :value     @query
                  :on-change #(dispatch-sync [:update-query (-> % .-target .-value)])}]
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
                      :onSelect   #(dispatch [:page-memories (dec (aget %2 "eventKey"))])

                      }]]
        )


      #_ (if (> @pages 1)
        [:div {:style {:text-align "center"}}
         [:ul {:class "pagination"}
          [:li {:class (if (= 0 @current) "disabled")}
           [:a {:on-click #(dispatch [:page-memories (dec @current)])} "«"]]
          (doall
            (for [i (range 0 @pages)]
              ^{:key i}
              [:li {:class    (if (= i @current) "active")
                    :on-click #(dispatch [:page-memories i])}
               [:a (str (inc i))]]
              ))
          [:li {:class (if (>= @current (dec @pages)) "disabled")}
           [:a {:on-click #(dispatch [:page-memories (inc @current)])} "»"]]]]
        ))
    )
  )

(defn memory-results []
  (let [busy?    (subscribe [:ui-state :is-searching?])
        memories (subscribe [:ui-state :memories])
        results  (reaction (:results @memories))]
    (fn []
      [panel (if @busy? "Loading..." "Memories")
       [:span
        (if (empty? @results)
          [:p "Nothing."]
          (for [memory @results]
            ^{:key (:id memory)}
            [:blockquote
             [:p {:dangerouslySetInnerHTML {:__html (md->html (:thought memory) :replacement-transformers md-transformers)}}]
             [:small (:created memory)]]
            ))
        [memory-pager]
        ]
       "panel-primary"]
      )))

(defn memory-list []
  (fn []
    [:span
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
           [:label {:for "inputLogin" :class "col-lg-2 col-sm-2 control-label"} "Username"]
           [:div {:class "col-sm-10 col-lg-10"}
            [:input {:type         "text"
                     :class        "formControl col-sm-8 col-lg-8"
                     :id           "inputLogin"
                     :placeholder  "user name"
                     :on-change    #(dispatch-sync [:update-credentials :username (-> % .-target .-value)])
                     :on-key-press #(dispatch-on-press-enter % [:auth-request @signup?])
                     :value        @username}]]]
          [:div {:class (str "form-group" @pw-class)}
           [:label {:for "inputPassword" :class "col-sm-2 col-lg-2 control-label"} "Password"]
           [:div {:class "col-sm-10 col-lg-10"}
            [:input {:type         "password"
                     :class        "formControl col-sm-8 col-lg-8"
                     :id           "inputPassword"
                     :on-change    #(dispatch-sync [:update-credentials :password (-> % .-target .-value)])
                     :on-key-press #(dispatch-on-press-enter % [:auth-request @signup?])
                     :value        @password}]]]
          (if @signup?
            [:div {:class (str "form-group" @pw2-class)}
             [:label {:for "inputPassword2" :class "col-sm-2 col-lg-2 control-label"} "Confirm:"]
             [:div {:class "col-sm-10 col-lg-10"}
              [:input {:type         "password"
                       :class        "formControl col-sm-8 col-lg-8"
                       :id           "inputPassword2"
                       :on-change    #(dispatch-sync [:update-credentials :password2 (-> % .-target .-value)])
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
      (dispatch [:set-ui-section :login]))
    (condp = @section
      :write [write-section]
      :remember [memory-list]
      [login-form]
      )
    )
  )

(defn header []
  (let [state  (subscribe [:ui-state :section])
        header (condp = @state
                 :write "Make a new memory"
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
  #_ (doto (History.)
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


