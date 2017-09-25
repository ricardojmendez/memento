(defproject memento "0.6"
  :description "Memento mori"
  :url "https://mementoapp.herokuapp.com/"

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.908" :scope "provided"]
                 [org.clojure/core.async "0.3.443"]
                 [bidi "2.1.2" :exclusions [ring/ring-core]]
                 [buddy/buddy-auth "1.4.1"]
                 [buddy/buddy-hashers "1.2.0"]
                 [buddy/buddy-sign "1.5.0"]
                 [clj-time "0.14.0"]
                 [cljs-ajax "0.7.1"]
                 [cljsjs/react-bootstrap "0.30.2-0" :exclusions [org.webjars.bower/jquery]]
                 [org.clojure/tools.cli "0.3.5"]
                 [com.andrewmcveigh/cljs-time "0.5.1"]
                 [com.taoensso/timbre "4.10.0"]
                 [compojure "1.6.0"]
                 [conman "0.6.8"]
                 [cprop "0.1.11"]
                 [jayq "2.5.4"]
                 [kibu/pushy "0.3.8"]
                 [luminus-immutant "0.2.3"]
                 [luminus-migrations "0.4.0"]
                 [luminus-nrepl "0.1.4"]
                 [markdown-clj "0.9.99"]
                 [metosin/compojure-api "1.1.11"]
                 [metosin/muuntaja "0.3.2"]
                 [metosin/ring-http-response "0.9.0"]
                 [mount "0.1.11"]
                 [org.jsoup/jsoup "1.10.3"]                 ; For cleaning out HTML tags
                 [org.postgresql/postgresql "42.1.4"]
                 [re-frame "0.9.4"]
                 [reagent-utils "0.2.1"]                    ; Used for reagent.cookies
                 [ring/ring-defaults "0.3.1"]               ; Used for anti-forgery
                 [ring-webjars "0.2.0"]
                 [ring/ring-defaults "0.3.1"]
                 [selmer "1.11.0"]]

  :min-lein-version "2.0.0"
  :jvm-opts ["-server" "-Dconf=.lein-env"]

  :heroku {:app-name      "mementoapp"
           :include-files ["target/uberjar/memento.jar"]}


  :main memento.core
  ;; Necessary at a global level for uberjar deployments to Heroku
  :uberjar-name "memento.jar"

  :plugins [[lein-cprop "1.0.3"]
            [lein-cljsbuild "1.1.7"]
            [lein-cloverage "1.0.9"]
            [lein-heroku "0.5.3"]
            [migratus-lein "0.5.0"]]


  :migratus {:store         :database
             :migration-dir "migrations"}


  :source-paths ["src/clj" "src/cljs" "src/cljc"]
  :resource-paths ["resources" "target/cljsbuild"]
  :target-path "target/%s/"
  :test-paths ["test/clj" "test/cljs" "test/cljc"]

  :clean-targets ^{:protect false} [:target-path
                                    [:cljsbuild :builds :app :compiler :output-dir]
                                    [:cljsbuild :builds :app :compiler :output-to]]

  :cljsbuild
  {:builds        {:app {:source-paths ["src/cljs"]
                         :compiler     {:asset-path    "/js/out"
                                        :externs       ["react/externs/react.js" "externs/jquery-1.9.js" "externs/misc-externs.js"]
                                        :optimizations :none
                                        :output-to     "target/cljsbuild/public/js/memento.js"
                                        :output-dir    "target/cljsbuild/public/js/out"
                                        :pretty-print  true}}
                   }
   :test-commands {"test" ["phantomjs" "phantom/unit-test.js" "phantom/unit-test.html"]}}


  :profiles
  {:uberjar      {:omit-source    true
                  :uberjar-name   "memento.jar"
                  :prep-tasks     ["clean" "compile" ["cljsbuild" "once"]]
                  :aot            :all
                  :source-paths   ["env/prod/clj"]
                  :resource-paths ["env/prod/resources"]
                  :hooks          [leiningen.cljsbuild]
                  :cljsbuild      {:jar true
                                   :builds
                                        {:app
                                         {:source-paths ["env/prod/cljs"]
                                          :compiler     {:optimizations    :advanced
                                                         :pretty-print     false
                                                         :closure-warnings {:externs-validation :off
                                                                            :non-standard-jsdoc :off}}}}}
                  }

   :dev          [:project/dev :profiles/dev]
   :test         [:project/test :profiles/test]

   :project/dev  {:dependencies   [[binaryage/devtools "0.9.4"]
                                   [prone "1.1.4"]
                                   [ring/ring-mock "0.3.1"]
                                   [ring/ring-devel "1.6.2"]
                                   [pjstadig/humane-test-output "0.8.2"]
                                   [figwheel-sidecar "0.5.13"]
                                   [com.cemerick/piggieback "0.2.2"]]
                  :source-paths   ["env/dev/clj"]
                  :resource-paths ["env/dev/resources"]
                  :plugins        [[lein-figwheel "0.5.13" :exclusions [org.clojure/clojure]]]
                  :cljsbuild      {:builds {:app {:source-paths ["env/dev/cljs"]
                                                  :compiler     {:main "memento.app"}}}}
                  :figwheel       {:http-server-root "public"
                                   :nrepl-port       7002
                                   :css-dirs         ["resources/public/css"]
                                   :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

                  :repl-options   {:init-ns user}
                  :injections     [(require 'pjstadig.humane-test-output)
                                   (pjstadig.humane-test-output/activate!)]
                  }
   :project/test {:source-paths   ["env/test/clj" "test/clj" "test/cljc" "test/cljs"]
                  :dependencies   [[ring/ring-mock "0.3.1"]] ; Added so I can run individual tests on a test REPL
                  ; :hooks          [leiningen.cljsbuild]
                  :resource-paths ["env/dev/resources" "env/test/resources"]
                  :cljsbuild      {:builds {:test {:source-paths ["src/cljs"]
                                                   :compiler
                                                                 {:output-dir    "target/test/"
                                                                  :externs       ["react/externs/react.js" "externs/jquery-1.9.js" "externs/misc-externs.js"]
                                                                  :optimizations :whitespace
                                                                  :pretty-print  true
                                                                  :output-to     "target/test/memento-tests.js"}}}}
                  }
   })
