(ns memento.routes.api.resolution
  (:require [memento.db.resolution :as resolution]
            [memento.misc.html :refer [remove-html-from-vals]]
            [clojure.string :as s]
            [ring.util.http-response :refer [ok unauthorized conflict created
                                             bad-request! not-found forbidden
                                             no-content]]))

(defn create!
  "Creates a new resolution after cleaning up the parameters."
  [username subject description alternatives outcomes tags]
  (let [item   (remove-html-from-vals
                 {:username     username
                  :subject      subject
                  :description  description
                  :alternatives alternatives
                  :outcomes     outcomes
                  :tags         (when tags (s/lower-case tags))}
                 :subject :description :alternatives :outcomes :tags)
        result (when (and (not-empty (:subject item))
                          (not-empty (:description item)))
                 (resolution/create! item))]
    (if result
      (created (str "/api/resolutions/" (:id result)) result)
      (bad-request!))))

(defn get-list
  "Gets all resolutions for a username, by inverse create date"
  [username]
  (ok (resolution/get-list username true)))

(defn get-one
  "Gets a single resolution for a username.

  Will return not-found if the resolution does not belong to the user."
  [username id]
  (if-let [item (resolution/get-if-owner username id)]
    (ok item)
    (not-found)))


  
