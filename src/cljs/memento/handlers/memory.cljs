(ns memento.handlers.memory
  (:require [ajax.core :refer [GET POST PUT PATCH DELETE]]
            [memento.handlers.auth :refer [clear-token-on-unauth]]
            [re-frame.core :refer [dispatch reg-sub reg-event-db subscribe dispatch-sync]]
            [taoensso.timbre :as timbre]
            [memento.handlers.thread :as thread]
            [memento.helpers :as helpers]))


;;;
;;; Handlers
;;;

(reg-event-db
  :memory-archive
  (fn [app-state [_ memory archived?]]
    (let [url (str "/api/thoughts/" (:id memory) "/archive")]
      (PUT url {:params        {:archived? archived?}
                :headers       {:authorization (str "Token " (get-in app-state [:credentials :token]))}
                :handler       #(dispatch [:memory-archive-success memory])
                :error-handler #(dispatch [:state-error "Error changing archive state" %])}))
    (assoc-in app-state [:ui-state :is-busy?] true)))

(reg-event-db
  :memory-archive-success
  (fn [app-state [_ memory]]
    (let [thread-id (:root-id memory)]
      (thread/reload-if-cached app-state thread-id)
      (dispatch [:memories-load])
      (-> app-state
          (assoc-in [:ui-state :is-busy?] false)
          (assoc :search-state nil)))))

(reg-event-db
  :memories-load
  (fn [app-state [_ page-index]]
    (let [q         (get-in app-state [:ui-state :current-query])
          last-q    (get-in app-state [:search-state :query])
          all?      (or (get-in app-state [:ui-state :query-all?]) false)
          last-all? (get-in app-state [:search-state :all?])
          same?     (and (= q last-q)
                         (= all? last-all?))
          list      (if same?
                      (get-in app-state [:search-state :list])
                      [])
          p         (or page-index (get-in app-state [:ui-state :results-page]))]
      (if (or (not same?)
              (> p (or (get-in app-state [:search-state :page-index]) -1)))
        (do (GET "/api/search" {:params        {:q q :page p :all? all?}
                                :headers       {:authorization (str "Token " (get-in app-state [:credentials :token]))}
                                :handler       #(dispatch [:memories-load-success %])
                                :error-handler #(dispatch [:state-error "Error remembering" %])
                                })
            (-> app-state
                (assoc-in [:ui-state :is-searching?] true)
                (assoc :search-state {:query       q
                                      :page-index  p
                                      :all?        all?
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
        (assoc-in [:search-state :list] (concat (get-in app-state [:search-state :list]) (helpers/add-html-to-thoughts (:results memories))))
        (assoc-in [:search-state :last-result] memories)
        (assoc-in [:ui-state :is-searching?] false))
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
      (PATCH url {:params        {:thought note}
                  :headers       {:authorization (str "Token " (get-in app-state [:credentials :token]))}
                  :handler       #(dispatch [:memory-edit-save-success memory note])
                  :error-handler #(dispatch [:state-error "Error editing memory" %])}))
    (assoc-in app-state [:ui-state :is-busy?] true)))


(reg-event-db
  :memory-edit-save-success
  (fn [app-state [_ memory msg]]
    (let [thread-id (:root-id memory)]
      (dispatch [:state-message (str "Updated memory to: " msg) "alert-success"])
      (dispatch [:memories-load])
      (thread/reload-if-cached app-state thread-id)
      (-> app-state
          (assoc-in [:ui-state :is-busy?] false)
          (assoc-in [:note :edit-memory] nil)
          (assoc-in [:note :edit-note] "")
          (assoc-in [:note :focus] nil)
          (assoc :search-state nil)
          ))))


(reg-event-db
  :memory-forget
  (fn [app-state [_ memory]]
    (let [url (str "/api/thoughts/" (:id memory))]
      (DELETE url {:headers       {:authorization (str "Token " (get-in app-state [:credentials :token]))}
                   :handler       #(dispatch [:memory-forget-success memory %])
                   :error-handler #(dispatch [:state-error "Error forgetting thought" %])}))
    app-state))

(reg-event-db
  :memory-forget-success
  (fn [app-state [_ memory msg]]
    (dispatch [:state-message (str "Thought forgotten") "alert-success"])
    (thread/reload-if-cached app-state (:root-id memory))
    (dispatch [:memories-load])
    (-> app-state
        (assoc-in [:ui-state :is-busy?] false)
        (assoc :search-state nil))))


(reg-event-db
  :memory-save
  (fn [app-state _]
    (let [note (get-in app-state [:note :current-note])]
      (POST "/api/thoughts" {:params        {:thought note :follow-id (get-in app-state [:note :focus :id])}
                             :headers       {:authorization (str "Token " (get-in app-state [:credentials :token]))}
                             :handler       #(dispatch [:memory-save-success % note])
                             :error-handler #(dispatch [:state-error "Error saving thought" %])}))
    (assoc-in app-state [:ui-state :is-busy?] true)))

(reg-event-db
  :memory-save-success
  (fn [app-state [_ result msg]]
    (dispatch [:state-message (str "Saved: " msg) "alert-success"])
    (dispatch [:reminder-create result "spaced"])
    (thread/reload-if-cached app-state (:root-id result))
    (-> app-state
        (assoc-in [:ui-state :is-busy?] false)
        (assoc-in [:note :current-note] "")
        (assoc-in [:ui-state :show-thread-id] nil)
        (assoc-in [:ui-state :show-thread?] false)
        (assoc-in [:note :focus] nil)
        (assoc :search-state nil))))
