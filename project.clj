(defproject memento "0.3"
  :description "Memento mori"
  :url "https://mementoapp.herokuapp.com/"

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.122" :scope "provided"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.clojure/java.jdbc "0.4.1"]
                 [org.clojure/tools.nrepl "0.2.10"]
                 [org.clojure/tools.reader "0.9.2"]
                 [bidi "1.21.0" :exclusions [ring/ring-core]]
                 [bouncer "0.3.3"]
                 [buddy/buddy-auth "0.6.2" :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [buddy/buddy-hashers "0.6.0"]
                 [buddy/buddy-sign "0.6.1"]
                 [clj-dbcp "0.8.1"]
                 [clj-time "0.11.0"]
                 [cljs-ajax "0.3.14"]
                 [cljsjs/react-bootstrap "0.25.1-0" :exclusions [org.webjars.bower/jquery]]
                 [com.taoensso/timbre "4.1.1"]
                 [com.taoensso/tower "3.1.0-beta3"]
                 [environ "1.0.0"]
                 [io.clojure/liberator-transit "0.3.0"]
                 [jayq "2.5.4"]
                 [kibu/pushy "0.3.3"]
                 [liberator "0.13"]
                 [markdown-clj "0.9.69"]
                 [metosin/ring-middleware-format "0.6.0"]
                 [metosin/ring-http-response "0.6.5"]
                 [migratus "0.8.4"]
                 [org.jsoup/jsoup "1.8.3"]
                 [org.postgresql/postgresql "9.4-1201-jdbc41"]
                 [prone "0.8.2"]
                 [reagent "0.5.0" :exclusions [cljsjs/react]]
                 [reagent-utils "0.1.5"]
                 [re-frame "0.4.1"]
                 [ring-server "0.4.0"]
                 [ring/ring-defaults "0.1.5"]
                 [ring/ring-session-timeout "0.1.0"]
                 [selmer "0.9.1"]
                 [to-jdbc-uri "0.3.0"]
                 [yesql "0.5.0"]
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
            [lein-cljsbuild "1.1.0"]
            [migratus-lein "0.1.3"]]



  :ring {:handler      memento.handler/app
         :init         memento.handler/init
         :destroy      memento.handler/destroy
         :uberwar-name "memento.war"}

  :migratus {:store         :database
             :migration-dir "migrations"}

  :clean-targets ^{:protect false} ["resources/public/js" "target"]

  :source-paths ["src/clj" "src/cljs" "src/cljc"]
  :test-paths ["test/clj" "test/cljs" "test/cljc"]

  :cljsbuild
  {:builds        {:app  {:source-paths
                          ["src/cljs"]
                          :compiler
                          {:output-dir    "resources/public/js/"
                           :externs       ["react/externs/react.js" "externs/jquery-1.9.js" "externs/misc-externs.js"]
                           :optimizations :none
                           :output-to     "resources/public/js/memento.js"
                           :source-map    "resources/public/js/memento.js.map"
                           :pretty-print  true}}
                   :test {:compiler
                          {:output-dir    "target/test/"
                           :externs       ["react/externs/react.js" "externs/jquery-1.9.js" "externs/misc-externs.js"]
                           :optimizations :whitespace
                           :pretty-print  true
                           :output-to     "target/test/memento-tests.js"}}
                   }
   :test-commands {"test" ["phantomjs" "phantom/unit-test.js" "phantom/unit-test.html"]}}


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
                            [lein-figwheel "0.3.9" :exclusions [org.clojure/clojure
                                                                org.clojure/tools.reader
                                                                org.codehaus.plexus/plexus-utils]]
                            [org.clojure/tools.nrepl "0.2.10"]]
             :source-paths ["env/dev/clj"]
             :plugins      [[lein-figwheel "0.3.9" :exclusions [org.clojure/clojure
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
                            :database-url "postgres://memento:testdb@localhost/memento_dev"
                            :auth-conf    {:passphrase "testpassword"
                                           :pubkey     "keys/dev_auth_pubkey.pem"
                                           :privkey    "keys/dev_auth_privkey.pem"}
                            }}
   :test    {:env          {:dev          true
                            :database-url "postgres://memento:testdb@localhost/memento_test"
                            :auth-conf    {:passphrase "testpassword"
                                           :pubkey     "keys/dev_auth_pubkey.pem"
                                           :privkey    "keys/dev_auth_privkey.pem"}
                            }
             :hooks        [leiningen.cljsbuild]
             :source-paths ["test/clj" "test/cljc" "test/cljs"]
             :cljsbuild    {:builds {:app {:source-paths ["env/dev/cljs"]}}}
             }
   })
