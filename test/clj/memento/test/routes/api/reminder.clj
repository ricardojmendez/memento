(ns memento.test.routes.api.reminder
  (:require [clojure.test :refer :all]
            [clj-time.coerce :as c]
            [clj-time.core :as t]
            [cognitect.transit :as transit]
            [memento.handler :refer [app]]
            [memento.db.user :as user]
            [memento.test.db.core :as tdb]
            [memento.test.db.memory :as tdm]
            [memento.test.db.user :as tdu]
            [memento.test.routes.helpers :refer [post-request get-request put-request del-request invoke-login]]
            [memento.db.core :refer [*db*] :as db]
            [ring.mock.request :refer [request header body]]
            [clojure.string :as string]
            [numergent.auth :as auth]))

;;;;
;;;; Tests
;;;;

;; TODO: Add reminder tests

(deftest empty-test
  (is true))

