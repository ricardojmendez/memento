(defproject memento "0.2-SNAPSHOT"
  :description "Memento mori"
  :url "https://mementoapp.herokuapp.com/"

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "0.0-3308" :scope "provided"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.clojure/java.jdbc "0.3.7"]
                 [org.clojure/tools.nrepl "0.2.10"]
                 [org.clojure/tools.reader "0.9.2"]
                 [bouncer "0.3.3"]
                 [buddy/buddy-auth "0.6.0" :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [buddy/buddy-hashers "0.6.0"]
                 [buddy/buddy-sign "0.6.0"]
                 [cljs-ajax "0.3.13"]
                 [cljsjs/react "0.13.3-0"]
                 [compojure "1.3.4"]
                 [com.taoensso/timbre "4.0.2"]
                 [com.taoensso/tower "3.1.0-beta3"]
                 [environ "1.0.0"]
                 [io.clojure/liberator-transit "0.3.0"]
                 [liberator "0.13"]
                 [markdown-clj "0.9.67"]
                 [metosin/ring-middleware-format "0.6.0"]
                 [metosin/ring-http-response "0.6.3"]
                 [migratus "0.8.2"]
                 [org.postgresql/postgresql "9.4-1201-jdbc41"]
                 [prone "0.8.2"]
                 [reagent "0.5.0"]
                 [reagent-utils "0.1.5"]
                 [re-frame "0.4.1"]
                 [ring-server "0.4.0"]
                 [ring/ring-defaults "0.1.5"]
                 [ring/ring-session-timeout "0.1.0"]
                 [selmer "0.8.2"]
                 [yesql "0.5.0-rc3"]
                 ]

  :min-lein-version "2.0.0"
  :uberjar-name "memento.jar"
  :jvm-opts ["-server"]

  ;;enable to start the nREPL server when the application launches
  ;:env {:repl-port 7001}

  :main memento.core

  :plugins [[lein-ring "0.9.1"]
            [lein-environ "1.0.0"]
            [lein-ancient "0.6.5"]
            [lein-cljsbuild "1.0.6"]
            [migratus-lein "0.1.3"]]



  :ring {:handler      memento.handler/app
         :init         memento.handler/init
         :destroy      memento.handler/destroy
         :uberwar-name "memento.war"}

  :migratus {:store         :database
             :migration-dir "migrations"
             }

  :clean-targets ^{:protect false} ["resources/public/js" "target"]

  :source-paths ["src/clj" "src/cljs" "src/cljc"]
  :test-paths ["test/clj" "test/cljc"]

  :cljsbuild
  {:builds
   {:app
    {:source-paths ["src/cljs"]
     :compiler
                   {:output-dir    "resources/public/js/"
                    :externs       ["react/externs/react.js"]
                    :optimizations :none
                    :output-to     "resources/public/js/memento.js"
                    :source-map    "resources/public/js/memento.js.map"
                    :pretty-print  true}}}}


  :profiles
  {:uberjar {:omit-source true
             :env         {:production   true
                           :cluster-name "memento"
                           :index-name   "memento"
                           :host-name    "localhost"
                           :auth-conf    {:passphrase "testpassword"
                                          :pubkey     "keys/dev_auth_pubkey.pem"
                                          :privkey    "keys/dev_auth_privkey.pem"}}
             :hooks       [leiningen.cljsbuild]
             :cljsbuild
                          {:jar true
                           :builds
                                {:app
                                 {:source-paths ["env/prod/cljs"]
                                  :compiler     {:optimizations :advanced :pretty-print false}}}}
             :aot         :all}
   :dev     {:dependencies [[ring-mock "0.1.5"]
                            [ring/ring-devel "1.4.0"]
                            [pjstadig/humane-test-output "0.7.0"]
                            [lein-figwheel "0.3.7" :exclusions [org.clojure/clojure
                                                                org.clojure/tools.reader
                                                                org.codehaus.plexus/plexus-utils]]
                            [org.clojure/tools.nrepl "0.2.10"]]
             :source-paths ["env/dev/clj"]
             :plugins      [[lein-figwheel "0.3.5" :exclusions [org.clojure/clojure
                                                                org.clojure/tools.reader
                                                                org.codehaus.plexus/plexus-utils]]]
             :cljsbuild    {:builds {:app {:source-paths ["env/dev/cljs"]}}}


             :figwheel     {:http-server-root "public"
                            :server-port      3449
                            :css-dirs         ["resources/public/css"]
                            :ring-handler     memento.handler/app}

             :repl-options {:init-ns memento.core}
             :injections   [(require 'pjstadig.humane-test-output)
                            (pjstadig.humane-test-output/activate!)]
             :env          {:dev          true
                            :database-url "postgresql://memento:testdb@localhost/memento_dev"
                            :auth-conf    {:passphrase "testpassword"
                                           :pubkey     "keys/dev_auth_pubkey.pem"
                                           :privkey    "keys/dev_auth_privkey.pem"}
                            }}
   :test    {:env          {:dev          true
                            :database-url "postgresql://memento:testdb@localhost/memento_test"
                            :auth-conf    {:passphrase "testpassword"
                                           :pubkey     "keys/dev_auth_pubkey.pem"
                                           :privkey    "keys/dev_auth_privkey.pem"}
                            }
             :source-paths ["test/clj" "test/cljc"]
             :cljsbuild    {:builds {:app {:source-paths ["env/dev/cljs"]}}}
             }
   })
