(ns memento.core
  (:require [ajax.core :refer [GET POST PUT]]
            [reagent.core :as reagent :refer [atom]]
            [reagent-forms.core :refer [bind-fields]]
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
(register-sub :credentials general-query)



;------------------------------
; Handlers
;------------------------------

(defn remove-token-on-unauth
  "Receives an application state and an authorization result. If the status is 401, then it
  removes the token from the applicaton state."
  [app-state result]
  (if (= 401 (:status result))
    (assoc-in app-state [:credentials :token] nil)
    app-state))

(register-handler
  :initialize
  (fn [app-state _]
    (merge app-state {:ui-state {:is-busy?      false
                                 :section       :login
                                 :current-query ""
                                 :is-searching? false}})))

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
                                      :handler       #(dispatch [:login-success (:token %)])
                                      :error-handler #(dispatch [:login-error %])}))
      )
    app-state
    ))

(register-handler
  :login-success
  (fn [app-state [_ token]]
    (-> app-state
        (assoc-in [:credentials :token] token)
        (assoc-in [:ui-state :section] :write)
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
    (assoc-in app-state [:ui-state :current-query] q)))

(register-handler
  :load-memories
  (fn [app-state _]
    (GET "/api/memory/search" {:params        {:q (get-in app-state [:ui-state :current-query])}
                               :headers       {:authorization (str "Token " (get-in app-state [:credentials :token]))}
                               :handler       #(dispatch [:load-memories-done %])
                               :error-handler #(dispatch [:set-message (str "Error remembering. " %) "alert-danger"])
                               })
    (-> app-state
        (assoc-in [:ui-state :memories] [])
        (assoc-in [:ui-state :is-searching?] true))
    ))

(register-handler
  :load-memories-done
  (fn [app-state [_ memories]]
    (-> app-state
        (assoc-in [:ui-state :memories] memories)
        (assoc-in [:ui-state :is-searching?] false))
    ))

(register-handler
  :save-note
  (fn [app-state _]
    (let [note (get-in app-state [:note :current-note])]
      (POST "/api/memory" {:params        {:thought note}
                           :headers       {:authorization (str "Token " (get-in app-state [:credentials :token]))}
                           :handler       #(dispatch [:save-note-success note])
                           :error-handler #(dispatch [:save-note-error %])}))
    app-state
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
    (-> app-state
        (assoc-in [:ui-state :is-busy?] false)
        (remove-token-on-unauth result))))


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
             [navbar-item "Write" :write]
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
    (dispatch d))
  )

(defn memory-list []
  (let [query    (subscribe [:ui-state :current-query])
        busy?    (subscribe [:ui-state :is-searching?])
        memories (subscribe [:ui-state :memories])]
    (fn []
      [:span
       [:div {:class "form-horizontal"}
        [:div {:class "form-group"}
         [:label {:for "input-search" :class "col-lg-2 control-label"} "Search:"]
         [:div {:class "col-lg-8"}
          [:input {:type         "text"
                   :class        "form-control"
                   :id           "input-search"
                   :value        @query
                   :on-change    #(dispatch-sync [:update-query (-> % .-target .-value)])
                   :on-key-press #(dispatch-on-press-enter % [:load-memories])}]]
         [:div {:class "col-lg-1"}
          [:button {:type "submit" :class "btn btn-primary" :on-click #(dispatch [:load-memories])} "Search"]]
         ]]
       (if @busy?
         [panel "Loading..." "Please wait while your memories are being loaded" "panel-info"]
         [panel "Memories"
          [:span
           (if (empty? @memories)
             [:p "Nothing."]
             (for [memory @memories]
               ^{:key (:id memory)}
               [:blockquote
                [:p (:thought memory)]
                [:small (:created memory)]]
               ))
           ]
          "panel-primary"
          ])
       ]
      )))

(defn login-form []
  (let [username  (subscribe [:credentials :username])
        password  (subscribe [:credentials :password])
        confirm   (subscribe [:credentials :password2])
        message   (subscribe [:credentials :message])
        section   (subscribe [:ui-state :section])
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
           [:label {:for "inputLogin" :class "col-lg-2 control-label"} "Username"]
           [:div {:class "col-lg-10"}
            [:input {:type         "text"
                     :class        "formControl col-lg-6"
                     :id           "inputLogin"
                     :placeholder  "user name"
                     :on-change    #(dispatch-sync [:update-credentials :username (-> % .-target .-value)])
                     :on-key-press #(dispatch-on-press-enter % [:auth-request @signup?])
                     :value        @username}]]]
          [:div {:class (str "form-group" @pw-class)}
           [:label {:for "inputPassword" :class "col-lg-2 control-label"} "Password"]
           [:div {:class "col-lg-10"}
            [:input {:type         "password"
                     :class        "formControl col-lg-6"
                     :id           "inputPassword"
                     :on-change    #(dispatch-sync [:update-credentials :password (-> % .-target .-value)])
                     :on-key-press #(dispatch-on-press-enter % [:auth-request @signup?])
                     :value        @password}]]]
          (if @signup?
            [:div {:class (str "form-group" @pw2-class)}
             [:label {:for "inputPassword2" :class "col-lg-2 control-label"} "Confirm:"]
             [:div {:class "col-lg-10"}
              [:input {:type         "password"
                       :class        "formControl col-lg-6"
                       :id           "inputPassword2"
                       :on-change    #(dispatch-sync [:update-credentials :password2 (-> % .-target .-value)])
                       :on-key-press #(dispatch-on-press-enter % [:auth-request @signup?])
                       :value        @confirm}]]])

          ]
         [:div {:class "modal-footer"}
          [:button {:type "button" :class "btn btn-primary" :on-click #(dispatch [:auth-request @signup?])} "Submit"]]
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

