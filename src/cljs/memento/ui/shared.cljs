(ns memento.ui.shared
  (:require [bidi.bidi :as bidi]
            [cljsjs.react-bootstrap]
            [jayq.core :refer [$]]
            [memento.handlers.routing :as r]
            [memento.helpers :as helpers]
            [re-frame.core :refer [dispatch reg-sub reg-event-db subscribe dispatch-sync]]
            [reagent.core :as reagent])
  (:require-macros [memento.misc.cljs-macros :refer [adapt-bootstrap]]
                   [reagent.ratom :refer [reaction]]))


;;;;------------------------------
;;;; Bootstrap components
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


;;;;------------------------------
;;;; Data and helpers
;;;;------------------------------

(def initial-focus-wrapper
  (with-meta identity
             {:component-did-mount #(.focus (reagent/dom-node %))}))

(defn dispatch-on-press-enter [e d]
  (if (= 13 (.-which e))
    (dispatch d)))

(defn find-dom-elem
  "Find a dom element by its id. Expects a keyword."
  [^Keyword id]
  (first ($ id)))

(def top-div-target (find-dom-elem :#header))


;;;;-------------------------
;;;; Components
;;;;-------------------------


(defn panel [title msg class]
  [:div {:class (str "panel " class)}
   [:div {:class "panel-heading"}
    [:h3 {:class "panel-title"} title]]
   [:div {:class "panel-body"} msg]])

(defn focused-thought []
  (let [focus (subscribe [:note :focus])]
    (if @focus
      [:div {:class "col-sm-10 col-sm-offset-1"}
       [:div {:class "panel panel-default"}
        [:div {:class "panel-heading"} "Following up ... " [:i [:small "(from " (helpers/format-date (:created @focus)) ")"]]
         [:button {:type "button" :class "close" :aria-hidden "true" :on-click #(dispatch [:state-refine nil])} "×"]]
        [:div {:class "panel-body"}
         [:p {:dangerouslySetInnerHTML {:__html (:html @focus)}}]]]])))

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
                     :value       @note}]]]])))

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
         [NavItem {:href "/about"} "About"]]]])))

(defn alert []
  (let [msg (subscribe [:ui-state :last-message])]
    (fn []
      (if (not-empty (:text @msg))
        [:div {:class (str "alert " (:class @msg))}
         [:button {:type :button :class "close" :on-click #(dispatch [:state-message ""])} "x"]
         (:text @msg)]))))