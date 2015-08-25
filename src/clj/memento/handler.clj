(ns memento.handler
  (:require [buddy.auth.backends.token :refer [token-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [clojure.tools.nrepl.server :as nrepl]
            [bidi.ring :refer [make-handler]]
            [environ.core :refer [env]]
            [memento.auth :as auth]
            [memento.db.migrations :as migrations]
            [memento.middleware :as middleware]
            [memento.routes.api :refer [api-routes not-found-route]]
            [memento.routes.home :refer [home-routes]]
            [memento.session :as session]
            [selmer.parser :as parser]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.3rd-party.rotor :as rotor]
            [memento.db.core :as db]))

(defonce nrepl-server (atom nil))


(defn start-nrepl
  "Start a network repl for debugging when the :repl-port is set in the environment."
  []
  (when-let [port (env :repl-port)]
    (try
      (reset! nrepl-server (nrepl/start-server :port port))
      (timbre/info "nREPL server started on port" port)
      (catch Throwable t
        (timbre/error "failed to start nREPL" t)))))

(defn stop-nrepl []
  (when-let [server @nrepl-server]
    (nrepl/stop-server server)))

(defn init
  "init will be called once when
   app is deployed as a servlet on
   an app server such as Tomcat
   put any initialization code here"
  []

  (timbre/merge-config!
    {:level     (if (env :dev) :trace :info)
     :appenders {:rotor (rotor/rotor-appender
                          {:path     "memento.log"
                           :max-size (* 512 1024)
                           :backlog  10})}})

  (timbre/info "-=[ Applying migrations ]=-")
  (migrations/migrate ["migrate"])
  (timbre/info "...migrations done")

  (if (env :dev) (parser/cache-off!))
  (start-nrepl)
  (db/connect!)
  ;;start the expired session cleanup job
  (session/start-cleanup-job!)
  (timbre/info (str
                 "\n-=[memento started successfully"
                 (when (env :dev) " using the development profile")
                 "]=-")))

(defn destroy
  "destroy will be called when your application
   shuts down, put any clean up code here"
  []
  (timbre/info "memento is shutting down...")
  (db/disconnect!)
  (stop-nrepl)
  (timbre/info "shutdown complete!"))


(defn auth-token-decode
  [_ token]
  (when-let [result (auth/decode-token token)]
    (:username result)))

;; Create an instance of auth backend.

(def auth-backend
  (token-backend {:authfn auth-token-decode}))


(def app
  (-> (make-handler ["" [api-routes home-routes not-found-route]])
      ; TODO Wrap-csrf only for the home-routes
      ; middleware/wrap-csrf
      (wrap-authentication auth-backend)
      (wrap-authorization auth-backend)
      middleware/wrap-base
      ))
