(ns banach.pagination-test
  (:require [banach.pagination :as p]
            [clojure.test :refer [deftest is]]
            [manifold.deferred :as md]
            [manifold.stream :as ms]))

(deftest paginated->stream-test
  (let [gets (atom 0)
        get! (fn [[marker n]]
               (is (= ::todo marker))
               (swap! gets inc)
               (md/success-deferred
                {:results (map (partial + (* 10 n)) (range 10))
                 :todo (when (< n 9) [::todo (inc n)])}))
        s (p/paginated->stream get! [::todo 0] :todo :results)]
    (is (< 0 @gets 10))
    (is (= (range 100) (ms/stream->seq s)))
    (is (= @gets 10))))
