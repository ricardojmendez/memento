(ns memento.db.core
  (:require
    [clojure.java.jdbc :as jdbc]
    [clj-dbcp.core :as dbcp]
    [yesql.core :refer [defqueries]]
    [cheshire.core :refer [generate-string parse-string]]
    [taoensso.timbre :as timbre]
    [to-jdbc-uri.core :refer [to-jdbc-uri]]
    [environ.core :refer [env]])
  (:import org.postgresql.util.PGobject
           org.postgresql.jdbc4.Jdbc4Array
           clojure.lang.IPersistentMap
           clojure.lang.IPersistentVector
           [java.sql BatchUpdateException
                     Date
                     Timestamp
                     PreparedStatement]))

(defonce conn (atom nil))

(defqueries "sql/queries.sql")

(def pool-spec
  {:adapter    :postgresql
   :init-size  1
   :min-idle   1
   :max-idle   4
   :max-active 32})

(defn connect! []
  (try
    (reset!
      conn
      {:datasource
       (dbcp/make-datasource
         (assoc pool-spec
           :jdbc-url (to-jdbc-uri (env :database-url))))})
    (catch Exception e
      (timbre/error "Error occured while connecting to the database!" e))))

(defn disconnect! []
  (when-let [ds (:datasource @conn)]
    (when-not (.isClosed ds)
      (.close ds)
      (reset! conn nil))))

(defn run
  "executes a Yesql query using the given database connection and parameter map
  the parameter map defaults to an empty map and the database conection defaults
  to the conn atom"
  ([query-fn] (run query-fn {}))
  ([query-fn query-map] (run query-fn query-map @conn))
  ([query-fn query-map db]
   (try
     (query-fn query-map {:connection db})
     (catch BatchUpdateException e
       (throw (or (.getNextException e) e))))))

(defn to-date [sql-date]
  (-> sql-date (.getTime) (java.util.Date.)))

(extend-protocol jdbc/IResultSetReadColumn
  Date
  (result-set-read-column [v _ _] (to-date v))

  Timestamp
  (result-set-read-column [v _ _] (to-date v))

  Jdbc4Array
  (result-set-read-column [v _ _] (vec (.getArray v)))

  PGobject
  (result-set-read-column [pgobj _metadata _index]
    (let [type  (.getType pgobj)
          value (.getValue pgobj)]
      (case type
        "json" (parse-string value true)
        "jsonb" (parse-string value true)
        "citext" (str value)
        value))))

(extend-type java.util.Date
  jdbc/ISQLParameter
  (set-parameter [v ^PreparedStatement stmt idx]
    (.setTimestamp stmt idx (Timestamp. (.getTime v)))))

(defn to-pg-json [value]
  (doto (PGobject.)
    (.setType "jsonb")
    (.setValue (generate-string value))))

(extend-protocol jdbc/ISQLValue
  IPersistentMap
  (sql-value [value] (to-pg-json value))
  IPersistentVector
  (sql-value [value] (to-pg-json value)))
