(defproject forty-one-socks "0.1.0-SNAPSHOT"
  :description "Simple match game in cljs+om+react"
  :url "http://tonsky.github.io/41-socks/"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2156"]
                 [org.clojure/core.async "0.1.278.0-76b25b-alpha"]
                 [om "0.5.0"]]
  :plugins [[lein-cljsbuild "1.0.2"]]
  :cljsbuild { 
    :builds [{:id "dev"
              :source-paths ["src"]
              :compiler {
                :output-to "41_socks.js"
                :output-dir "out"
                :optimizations :none
                :source-map true}}
             {:id "prod"
              :source-paths ["src"]
              :compiler {
                :externs  ["react/externs/react.js"]
                :preamble ["react/react.min.js"]
                :pretty-print false
                :output-to "41_socks.min.js"
                :optimizations :advanced}}
             ]})
