(ns banach.retry-test
  (:require [clojure.test :refer :all]
            [manifold.deferred :as md]
            [manifold.time :as mt]
            [clojure.math.numeric-tower :as math]
            [banach.retry :as retry]))

(deftest exponentially-tests
  (testing "wait exponentially as failure count increases"
    (let [c (mt/mock-clock)
          strategy (#'retry/exponentially 10)
          delay-is (fn [delay failures]
                     (let [ctx {:failures failures}
                           d (strategy (md/success-deferred ctx))
                           delay-ms (* 1000 delay)]
                       (is (not (md/realized? d)))
                       (mt/advance c (dec delay-ms))
                       (is (not (md/realized? d)))
                       (mt/advance c 1)
                       (is (md/realized? d))))]
      (mt/with-clock c
        (delay-is 1 [])
        (delay-is 10 [:a])
        (delay-is 1000 [:a :b :c])))))

(deftest up-to-tests
  (testing "raises most recent exception when number of tries exceeded"
    (let [tries [(Exception. "earlier")
                 (Exception. "recent")]
          stop 2
          retry? #(throw (Exception. "I shouldn't have been called"))
          f (#'retry/up-to stop retry?)]
      (is (thrown-with-msg? Exception #"recent" (f tries)))))
  (testing "returns the result of retry when number of tries less than max"
    (let [tries []
          retry? (constantly :success)
          stop 2
          f (#'retry/up-to stop retry?)]
      (is (= :success (f tries))))))

(deftest retry-tests
  (testing "retry returns the result of f on success"
    (let [c (mt/mock-clock)
          called (atom false)
          strategy (fn [_failures] (reset! called true))
          f #(md/success-deferred :finished)
          ret (#'retry/retry f strategy)]
      ;; no clock necessary, time won't be a factor in test.
      (is (not @called))
      (is (= :finished @ret))))
  (testing "retries calling f"
    (let [c (mt/mock-clock)
          attempts (atom 0)
          strategy (fn [failures]
                     (swap! attempts inc)
                     ;; allow one failure, then explode
                     (if (= (count failures) 1)
                       2
                       (throw (last failures))))
          f #(md/error-deferred (Exception. "I've failed you"))]
      (mt/with-clock c
        (let [ret (#'retry/retry f strategy)]
          (is (= 1 @attempts))

          (mt/advance c (mt/seconds 2))
          (is (= 2 @attempts))
          (is (thrown-with-msg? Exception #"I've failed you" @ret)))))))

(deftest retry-exp-backoff-tests
  (testing "retries until stop is reached and re-throws last exception"
    (let [c (mt/mock-clock)
          attempts (atom 0)
          p 4
          f (fn []
              (swap! attempts inc)
              (md/error-deferred (Exception. "explosion")))
          stop 3]
      (mt/with-clock c
        (let [ret (retry/retry-exp-backoff f p stop)]
          (is (= 1 @attempts))

          (mt/advance c (mt/seconds p))
          (is (= 2 @attempts))

          (mt/advance c (mt/seconds (* p p)))
          (is (= 3 @attempts))
          (is (thrown-with-msg? Exception #"explosion" @ret))))))
  (testing "returns success deferred on completion"
    (let [c (mt/mock-clock)
          v "hi"
          stop 1
          p 5
          f #(md/success-deferred v)]
      (mt/with-clock c
        (let [ret (retry/retry-exp-backoff f p stop)]
          (mt/advance c 1)
          (is (= v @ret)))))))
