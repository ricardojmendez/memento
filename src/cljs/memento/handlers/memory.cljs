(ns memento.handlers.memory
  (:require [ajax.core :refer [GET POST PUT DELETE]]
            [memento.handlers.auth :refer [clear-token-on-unauth]]
            [re-frame.core :refer [dispatch reg-sub reg-event-db subscribe dispatch-sync]]
            [taoensso.timbre :as timbre]
            [memento.helpers :as helpers]))


;;;
;;; Helpers
;;;

(defn thread-in-cache?
  "Receives an application state and a thread-id, and returns true if the
  application cache currently contains that thread."
  [app-state thread-id]
  (contains? (get-in app-state [:cache :threads]) thread-id))


;;;
;;; Handlers
;;;

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
        (assoc-in [:search-state :list] (concat (get-in app-state [:search-state :list]) (helpers/add-html-to-thoughts (:results memories))))
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
    (assoc-in app-state [:ui-state :is-busy?] false)))
