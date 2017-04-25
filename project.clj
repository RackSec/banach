(defproject banach "0.2.1-SNAPSHOT"
  :description "Utility functions to use with manifold"
  :url "https://github.com/racksec/banach"
  :lein-release {:deploy-via :clojars}
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [manifold "0.1.6"]
                 [org.clojure/math.numeric-tower "0.0.4"]]
  :plugins [[lein-cljfmt "0.5.6"]
            [lein-ancient "0.6.10"]
            [lein-cloverage "1.0.9"]]
  :cljfmt {:indents {let-flow [[:inner 0]]
                     catch [[:inner 0]]}})
