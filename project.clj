(defproject banach "0.1.0-SNAPSHOT"
  :description "Utility functions to use with manifold"
  :url "https://github.com/racksec/banach"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [com.taoensso/timbre "4.3.1"]
                 [manifold "0.1.4"]
                 [org.clojure/math.numeric-tower "0.0.4"]]
  :plugins [[lein-auto "0.1.2"]
            [lein-cljfmt "0.3.0"]
            [lein-pprint "1.1.1"]
            [lein-environ "1.0.2"]
            [lein-ancient "0.6.10"]
            [lein-cloverage "1.0.7-SNAPSHOT"]])
