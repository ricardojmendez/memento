(ns memento.handlers.cluster
  (:require [ajax.core :refer [GET POST PUT PATCH]]
            [re-frame.core :refer [dispatch reg-sub reg-event-db reg-event-fx subscribe dispatch-sync]]
            [taoensso.timbre :as timbre]
            [memento.helpers :as helpers]))

(reg-event-fx
  :cluster-create
  (fn [{:keys [db]} [_ thought-ids]]
    (POST "/api/clusters"
          {:params        {:thought-ids thought-ids}
           :headers       {:authorization (str "Token " (get-in db [:credentials :token]))}
           :handler       #(dispatch [:state-message "Created cluster" "alert-success"])
           :error-handler #(dispatch [:state-message (str "Error creating thought cluster: " %) "alert-danger"])})
    nil))

(reg-event-fx
  :clusters-load-all
  (fn [{:keys [db]} [_]]
    (GET "/api/clusters"
          {:headers       {:authorization (str "Token " (get-in db [:credentials :token]))}
           :handler       #(dispatch [:clusters-load-thoughts %])
           :error-handler #(dispatch [:state-message (str "Error loading clusters: " %) "alert-danger"])})
    nil))


(reg-event-db
  :clusters-load-thoughts
  (fn [db [_ clusters]]
    (doseq [cluster clusters]
      (GET (str "/api/clusters/" (:id cluster))
           {:headers       {:authorization (str "Token " (get-in db [:credentials :token]))}
            :handler       #(dispatch [:cluster-loaded (:id cluster) %])
            :error-handler #(dispatch [:state-message (str "Error loading thoughts: " %) "alert-danger"])}))
    ;; Convert the list to a dictionary so that we can more easily assoc-in later.
    (assoc-in db [:cache :clusters] (reduce #(assoc %1 (:id %2) %2) {} clusters))))

(reg-event-db
  :cluster-loaded
  (fn [db [_ cluster-id thoughts]]
    (assoc-in db [:cache :clusters cluster-id :thoughts]
              (helpers/add-html-to-thoughts (:results thoughts)))))