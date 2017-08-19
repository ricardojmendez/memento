(ns memento.handlers.cache
  (:require [ajax.core :refer [GET POST PUT PATCH]]
            [re-frame.core :refer [dispatch reg-sub reg-event-db subscribe dispatch-sync]]
            [taoensso.timbre :as timbre]
            [memento.helpers :as helpers]))


(reg-event-db
  :cache-remove-reminder-display
  (fn [app-state [_ item]]
    ;; Only remove a reminder from the list of elements to see
    (let [new-reminder-list (not-empty (remove #(= (:id item) (:id %))
                                               (get-in app-state [:cache :reminders])))]
      (when (empty? new-reminder-list)
        (dispatch [:state-show-reminders false]))
      (assoc-in app-state [:cache :reminders] new-reminder-list))))

(reg-event-db
  :cache-remove-reminder
  (fn [app-state [_ item]]
    ;; When clearing a reminder, I have to remove it from the caches on both
    ;; threads and thought lists, to avoid having to refresh everything.
    ;; I also need to remove it from the list of reminders being displayed, obviously.
    (let [new-reminder-list (not-empty (remove #(= (:id item) (:id %)) (get-in app-state [:cache :reminders])))
          fn-rem-reminder   (fn [e] (assoc e :reminders
                                             (remove #(= (:id item) (:id %))
                                                     (:reminders e))))
          new-search-list   (map fn-rem-reminder (get-in app-state [:search-state :list]))
          threads           (get-in app-state [:cache :threads])
          new-threads       (into {}
                                  (for [[id thread] threads]
                                    [id (map fn-rem-reminder thread)]))
          ]
      (when (empty? new-reminder-list)
        (dispatch [:state-show-reminders false]))
      (-> app-state
          (assoc-in [:cache :reminders] new-reminder-list)
          (assoc-in [:cache :threads] new-threads)
          (assoc-in [:search-state :list] new-search-list)))))

(reg-event-db
  :cache-add-reminder
  (fn [app-state [_ item]]
    ;; When adding a reminder, we need to add it to the corresponding thought
    ;; on the cached lists, so that the indicators change.
    ;; We don't touch the reminder list, since there's no reason to expect a
    ;; reminder would be immediately displayed.
    (let [fn-add-reminder (fn [e] (if (= (:id e) (:thought_id item))
                                    (assoc e :reminders (conj (:reminders e) item))
                                    e))
          new-search-list (map fn-add-reminder (get-in app-state [:search-state :list]))
          threads         (get-in app-state [:cache :threads])
          new-threads     (into {}
                                (for [[id thread] threads]
                                  [id (map fn-add-reminder thread)]))
          ]
      (-> app-state
          (assoc-in [:cache :threads] new-threads)
          (assoc-in [:search-state :list] new-search-list)))
    ))