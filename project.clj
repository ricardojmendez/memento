(defproject memento "0.5"
  :description "Memento mori"
  :url "https://mementoapp.herokuapp.com/"

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.229" :scope "provided"]
                 [org.clojure/core.async "0.2.391"]
                 [bidi "2.0.10" :exclusions [ring/ring-core]]
                 [buddy/buddy-auth "1.2.0"]
                 [buddy/buddy-hashers "1.0.0"]
                 [buddy/buddy-sign "1.2.0"]
                 [clj-time "0.12.0"]
                 [cljs-ajax "0.5.8"]
                 [cljsjs/react-bootstrap "0.30.2-0" :exclusions [org.webjars.bower/jquery]]
                 [org.clojure/tools.cli "0.3.5"]
                 [com.taoensso/timbre "4.7.4"]
                 [conman "0.6.0"]
                 [cprop "0.1.9"]
                 [io.clojure/liberator-transit "0.3.0"]
                 [jayq "2.5.4"]
                 [kibu/pushy "0.3.6"]
                 [liberator "0.14.1"]
                 [luminus-immutant "0.2.2"]
                 [luminus-migrations "0.2.6"]
                 [luminus-nrepl "0.1.4"]
                 [markdown-clj "0.9.89"]
                 [metosin/ring-middleware-format "0.6.0"]
                 [metosin/ring-http-response "0.8.0"]
                 [mount "0.1.10"]
                 [org.jsoup/jsoup "1.9.2"]
                 [org.postgresql/postgresql "9.4.1210"]
                 [reagent "0.6.0" :exclusions [cljsjs/react]]
                 [ring/ring-defaults "0.2.1"]               ; Used for anti-forgery
                 [reagent-utils "0.2.0"]
                 [re-frame "0.8.0"]
                 [ring/ring-session-timeout "0.1.0"]
                 [selmer "1.0.7"]]

  :min-lein-version "2.0.0"
  :uberjar-name "memento.jar"
  :jvm-opts ["-server" "-Dconf=.lein-env"]

  :main memento.core

  :plugins [[lein-cprop "1.0.1"]
            [lein-cljsbuild "1.1.1"]
            [migratus-lein "0.3.9"]]


  :migratus {:store         :database
             :migration-dir "migrations"}

  :clean-targets ^{:protect false} ["resources/public/js" "target"]

  :source-paths ["src/clj" "src/cljs" "src/cljc"]
  :resource-paths ["resources"]
  :test-paths ["test/clj" "test/cljs" "test/cljc"]

  :cljsbuild
  {:builds        {:app  {:source-paths ["src/cljs"]
                          :compiler     {:output-dir    "resources/public/js/"
                                         :externs       ["react/externs/react.js" "externs/jquery-1.9.js" "externs/misc-externs.js"]
                                         :optimizations :none
                                         :output-to     "resources/public/js/memento.js"
                                         :pretty-print  true}}
                   :test {:source-paths ["src/cljs"]
                          :compiler
                                        {:output-dir    "target/test/"
                                         :externs       ["react/externs/react.js" "externs/jquery-1.9.js" "externs/misc-externs.js"]
                                         :optimizations :whitespace
                                         :pretty-print  true
                                         :output-to     "target/test/memento-tests.js"}}
                   }
   :test-commands {"test" ["phantomjs" "phantom/unit-test.js" "phantom/unit-test.html"]}}


  :profiles
  {:uberjar      {:omit-source    true
                  :aot            :all
                  :source-paths   ["env/prod/clj"]
                  :resource-paths ["env/prod/resources"]
                  :hooks          [leiningen.cljsbuild]
                  :cljsbuild      {:jar true
                                   :builds
                                        {:app
                                         {:source-paths ["env/prod/clj" "env/prod/cljs"]
                                          :compiler     {:optimizations :advanced :pretty-print false}}}}
                  }

   :dev          [:project/dev :profiles/dev]
   :test         [:project/test :profiles/test]

   :project/dev  {:dependencies   [[binaryage/devtools "0.8.1"]
                                   [prone "1.1.2"]
                                   [ring-mock "0.1.5"]
                                   [ring/ring-devel "1.5.0"]
                                   [pjstadig/humane-test-output "0.8.1"]
                                   [figwheel-sidecar "0.5.7"]
                                   [com.cemerick/piggieback "0.2.2-SNAPSHOT"]]
                  :source-paths   ["env/dev/clj"]
                  :resource-paths ["env/dev/resources"]
                  :plugins        [[lein-figwheel "0.5.7" :exclusions [org.clojure/clojure]]]
                  :cljsbuild      {:builds {:app {:source-paths ["env/dev/cljs"]}}}
                  :figwheel       {:http-server-root "public"
                                   :nrepl-port       7002
                                   :css-dirs         ["resources/public/css"]
                                   :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

                  :repl-options   {:init-ns user}
                  :injections     [(require 'pjstadig.humane-test-output)
                                   (pjstadig.humane-test-output/activate!)]
                  }
   :project/test {:hooks          [leiningen.cljsbuild]
                  :source-paths   ["test/clj" "test/cljc" "test/cljs"]
                  :resource-paths ["env/dev/resources" "env/test/resources"]
                  :cljsbuild      {:builds {:app {:source-paths ["env/dev/cljs"]}}}
                  }
   })
