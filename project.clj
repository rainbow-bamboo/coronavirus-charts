(defproject coronavirus-charts "0.1.0-SNAPSHOT"

  :description "Cite data associated with the novel coronavirus (covid-19)"
  :url "http://coronavirus-charts.org"

  :dependencies [[cheshire "5.10.0"] ;; confirm that we're not using anywhere
                 [metosin/jsonista "0.2.5"]
                 [clojure.java-time "0.3.2"]
                 [cprop "0.1.16"]
                 [expound "0.8.4"]
                 [funcool/struct "1.4.0"]
                 [luminus-immutant "0.2.5"]
                 [luminus-transit "0.1.2"]
                 [markdown-clj "1.10.2"]
                 [metosin/muuntaja "0.6.6"]
                 [metosin/reitit "0.4.2"]
                 [metosin/ring-http-response "0.9.1"]
                 [mount "0.1.16"]
                 [nrepl "0.6.0"]
                 [org.clojure/clojure "1.10.1"]
                 [org.clojure/tools.cli "1.0.194"]
                 [org.clojure/tools.logging "1.0.0"]
                 [org.webjars.npm/bulma "0.8.0"]
                 [org.webjars.npm/material-icons "0.3.1"]
                 [org.webjars/webjars-locator "0.39"]
                 [org.webjars/webjars-locator-jboss-vfs "0.1.0"]
                 [ring-webjars "0.2.0"]
                 [ring/ring-core "1.8.0"]
                 [ring/ring-defaults "0.3.2"]
                 [tick "0.4.23-alpha"]
                 [org.clojure/math.numeric-tower "0.0.4"]
                 [hiccup "1.0.5"]
                 [herb "0.10.0"]
                 [com.cerner/clara-rules "0.20.0"]
                 [org.clojars.pallix/analemma "1.0.0"]
                 [clj-http "3.10.0"]
                 [selmer "1.12.18"]]

  :min-lein-version "2.0.0"

  :source-paths ["src/clj"]
  :test-paths ["test/clj"]
  :resource-paths ["resources"]
  :target-path "target/%s/"
  :main ^:skip-aot coronavirus-charts.core

  :plugins [[lein-immutant "2.1.0"]
            [lein-kibit "0.1.2"]]

  :profiles
  {:uberjar {:omit-source true
             :aot :all
             :uberjar-name "coronavirus-charts.jar"
             :source-paths ["env/prod/clj" ]
             :resource-paths ["env/prod/resources"]}

   :dev           [:project/dev :profiles/dev]
   :test          [:project/dev :project/test :profiles/test]

   :project/dev  {:jvm-opts ["-Dconf=dev-config.edn" ]
                  :dependencies [[pjstadig/humane-test-output "0.10.0"]
                                 [prone "2020-01-17"]
                                 [ring/ring-devel "1.8.0"]
                                 [ring/ring-mock "0.4.0"]]
                  :plugins      [[com.jakemccrary/lein-test-refresh "0.24.1"]
                                 [jonase/eastwood "0.3.5"]]

                  :source-paths ["env/dev/clj" ]
                  :resource-paths ["env/dev/resources"]
                  :repl-options {:init-ns user
                                 :timeout 120000}
                  :injections [(require 'pjstadig.humane-test-output)
                               (pjstadig.humane-test-output/activate!)]}
   :project/test {:jvm-opts ["-Dconf=test-config.edn" ]
                  :resource-paths ["env/test/resources"] }
   :profiles/dev {}
   :profiles/test {}})
