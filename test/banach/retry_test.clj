(ns banach.retry-test
  (:require [clojure.test :refer :all]
            [manifold.deferred :as md]
            [manifold.time :as mt]
            [clojure.math.numeric-tower :as math]
            [banach.retry :as retry]))

(deftest exponentially-tests
  (testing "wait exponentially as failure count increases"
    (let [c (mt/mock-clock)
          strategy (retry/exponentially 10)
          delay-is (fn [delay failures]
                     (let [ctx {:failures failures}
                           d (strategy (md/success-deferred ctx))
                           delay-ms (mt/seconds delay)]
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
    (let [ctx {:failures [(Exception. "earlier") (Exception. "recent")]}
          strategy (retry/up-to 2)]
      (is (thrown-with-msg?
           Exception #"recent"
           @(strategy (md/success-deferred ctx))))))
  (testing "returns the ctx when tries remaining"
    (let [ctx {:failures []}
          strategy (retry/up-to 2)]
      (is (= ctx @(strategy ctx))))))

(deftest retry-tests
  (testing "retry returns the result of f on success"
    (let [called (atom false)
          strategy (fn [ctx] (reset! called true) ctx)
          f #(md/success-deferred :finished)
          ret (retry/retry f strategy)]
      (is (not @called))
      (is (= :finished @ret))))
  (testing "retries calling f"
    (let [c (mt/mock-clock)
          attempts (atom 0)
          strategy (fn [{:keys [failures] :as ctx}]
                     (swap! attempts inc)
                     ;; allow one failure, then explode
                     (if (= (count failures) 1)
                       (mt/in (mt/seconds 1) #(md/success-deferred ctx))
                       (throw (last failures))))
          f #(md/error-deferred (Exception. "I've failed you"))]
      (mt/with-clock c
        (let [ret (retry/retry f strategy)]
          (is (= 1 @attempts))
          (mt/advance c (mt/seconds 1))
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
