(ns memento.db
  (:require [environ.core :refer [env]]
            [clojurewerkz.elastisch.native :as es]
            [clojurewerkz.elastisch.native.index :as esi]
            [clojurewerkz.elastisch.native.document :as esd])
  (:import (java.util Date)))



(defn get-connection []
  (es/connect [[(:host-name env) 9300]] {"cluster.name" (:cluster-name env)}))

(def index-name (:index-name env))

(defn now [] (Date.))



(defn initialize-index! []
  (let [conn          (get-connection)
        mapping-types {"memory" {:properties {:username {:type "string" :store "yes"}
                                              :date     {:type "date" :store "yes"}
                                              :text     {:type "string" :analyzer "snowball" :term_vector "with_positions_offsets"}
                                              }}}]
    ; Commented out by default so that we don't accidentally wipe everything
    #_ (esi/delete conn index-name)
    (esi/create conn index-name :mappings mapping-types)
    ))

(defn save-memory!
  "Trivial save. For now everything will go to one user."
  [memory]
  (esd/create (get-connection) index-name "memory" (merge {:date (now) :username "ricardo"} memory)))