(ns memento.db
  (:require [environ.core :refer [env]]
            [clojurewerkz.elastisch.native :as es]
            [clojurewerkz.elastisch.native.index :as esi]
            [clojurewerkz.elastisch.native.document :as esd]
            [clojurewerkz.elastisch.query :as q])
  (:import (java.util Date)))



(defn get-connection []
  (es/connect [[(:host-name env) 9300]] {"cluster.name" (:cluster-name env)}))

(def index-name (:index-name env))

(defn now [] (Date.))


(defn flush-index! [conn]
  (esi/flush conn index-name))

(defn initialize-index! [conn]
  (let [conn          (get-connection)
        mapping-types {"memory" {:properties {:username {:type "string" :store "yes"}
                                              :date     {:type "date" :store "yes"}
                                              :text     {:type "string" :analyzer "snowball" :term_vector "with_positions_offsets"}
                                              }}}]
    ; Sanity check - we do not run this unless it's an index with "test" on the name.
    ; Yes, this means we'll need to figure out initialization for launch, but will deal
    ; with that when I deploy.
    (if (not (re-seq #"-test" index-name))
      (throw (Exception. (str "Not allowed on any but a test index. Current index: " index-name))))
    (if (esi/exists? conn index-name)
      (esi/delete conn index-name))
    (esi/create conn index-name :mappings mapping-types)
    (flush-index! conn)
    ))

(defn save-memory!
  "Trivial save. For now everything will go to one user."
  [conn memory]
  (esd/create conn index-name "memory" (merge {:date (now) :username "ricardo"} memory)))


(defn query-memories
  "Trivial query - return everything from one user"
  ([conn]
   (query-memories conn nil))
  ([conn query-str]
   (let [base-query [(q/term :username "ricardo")]
         query      (if (empty? query-str) base-query (conj base-query (q/match :text query-str)))]
     (-> (esd/search conn index-name "memory"
                     :query (q/bool {:must query})
                     :sort {:date "desc"}
                     :size 25)
         (get-in [:hits :hits])
         doall))))
