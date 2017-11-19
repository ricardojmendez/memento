(ns memento.ui.login
  (:require [memento.ui.shared :refer [dispatch-on-press-enter]]
            [re-frame.core :refer [dispatch reg-sub reg-event-db subscribe dispatch-sync]])
  (:require-macros [reagent.ratom :refer [reaction]]))


(defn login-form []
  (let [username  (subscribe [:credentials :username])
        password  (subscribe [:credentials :password])
        confirm   (subscribe [:credentials :password2])
        message   (subscribe [:credentials :message])
        section   (subscribe [:ui-state :section])
        wip?      (subscribe [:ui-state :wip-login?])
        signup?   (reaction (= :signup @section))
        u-class   (reaction (when (and @signup? (empty? @username)) " has-error"))
        pw-class  (reaction (when (and @signup? (> 5 (count @password))) " has-error"))
        pw2-class (reaction (when (not= @password @confirm) " has-error"))]
    (fn []
      [:div {:class "modal"}
       [:div {:class "modal-dialog"}
        [:div {:class "jumbotron"}
         [:h3 "Welcome!"]
         [:p "Memento is an experimental note-taking application "
          "for thoughts and ideas you may want to revisit."]
         [:p [:a {:class "btn btn-primary"
                  :href  "/about"}
              "Learn more"]]]
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
                       :value        @confirm}]]])]
         [:div {:class "modal-footer"}
          [:button {:type "button" :class "btn btn-primary" :disabled @wip? :on-click #(dispatch [:auth-request @signup?])} "Submit"]]]]])))
