(defproject memento "0.1.0-SNAPSHOT"
            :description "Memento mori"
            :url "http://numergent.com"

            :dependencies [[org.clojure/clojure "1.7.0-RC1"]
                           [selmer "0.8.2"]
                           [com.taoensso/timbre "3.4.0"]
                           [com.taoensso/tower "3.0.2"]
                           [markdown-clj "0.9.66"]
                           [environ "1.0.0"]
                           [compojure "1.3.4"]
                           [ring/ring-defaults "0.1.5"]
                           [ring/ring-session-timeout "0.1.0"]
                           [metosin/ring-middleware-format "0.6.0"]
                           [metosin/ring-http-response "0.6.2"]
                           [bouncer "0.3.2"]
                           [prone "0.8.2"]
                           [org.clojure/tools.nrepl "0.2.10"]
                           [ring-server "0.4.0"]
                           [org.clojure/clojurescript "0.0-3297" :scope "provided"]
                           [org.clojure/tools.reader "0.9.2"]
                           [reagent "0.5.0"]
                           [cljsjs/react "0.13.3-0"]
                           [reagent-forms "0.5.1"]
                           [reagent-utils "0.1.4"]
                           [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                           [cljs-ajax "0.3.11"]
                           [re-frame "0.4.1"]
                           [liberator "0.13"]
                           [io.clojure/liberator-transit "0.3.0"]
                           [clojurewerkz/elastisch "2.1.0"]
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
                      [lein-cljsbuild "1.0.6"]]



            :ring {:handler      memento.handler/app
                   :init         memento.handler/init
                   :destroy      memento.handler/destroy
                   :uberwar-name "memento.war"}

            :clean-targets ^{:protect false} ["resources/public/js" "target"]

            :cljsbuild
            {:builds
             {:app
              {:source-paths ["src-cljs"]
               :compiler
                             {:output-dir    "resources/public/js/out"
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
                                     :host-name    "localhost"}
                       :hooks       [leiningen.cljsbuild]
                       :cljsbuild
                                    {:jar true
                                     :builds
                                          {:app
                                           {:source-paths ["env/prod/cljs"]
                                            :compiler     {:optimizations :advanced :pretty-print false}}}}
                       :aot         :all}
             :dev     {:dependencies [[ring-mock "0.1.5"]
                                      [ring/ring-devel "1.3.2"]
                                      [pjstadig/humane-test-output "0.7.0"]
                                      [lein-figwheel "0.3.3"]
                                      [org.clojure/tools.nrepl "0.2.10"]]
                       :source-paths ["env/dev/clj"]
                       :plugins      [[lein-figwheel "0.3.3"]]
                       :cljsbuild
                                     {:builds {:app {:source-paths ["env/dev/cljs"]}}}

                       :figwheel
                                     {:http-server-root "public"
                                      :server-port      3449
                                      :css-dirs         ["resources/public/css"]
                                      :ring-handler     memento.handler/app}

                       :repl-options {:init-ns memento.core}
                       :injections   [(require 'pjstadig.humane-test-output)
                                      (pjstadig.humane-test-output/activate!)]
                       :env          {:dev          true
                                      :cluster-name "memento"
                                      :index-name   "memento"
                                      :host-name    "localhost"}}})
