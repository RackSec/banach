(ns banach.retry
  (:require
   [clojure.math.numeric-tower :as math]
   [manifold.deferred :as md]
   [manifold.time :as mt]))

(defn ^:private exponentially
  "Returns a strategy that causes an exponentially increasing wait before
  retrying. The base wait is measured in seconds."
  [wait]
  (fn [d]
    (md/let-flow [{:keys [failures] :as ctx} d
                  delay-ms (mt/seconds (math/expt wait (count failures)))]
      (mt/in delay-ms #(md/success-deferred ctx)))))

(defn ^:private up-to
  "Returns a strategy that allows up to n retries. "
  [stop]
  (fn [d]
    (md/let-flow [{:keys [failures] :as ctx} d]
      (if (< (count failures) stop)
        ctx
        (throw (last failures))))))

(defn ^:private retry
  "Retry a function multiple times, pausing for a number of seconds between
  each try.

  f -  a function that should be retried; must return a deferred
  strategy - a retry strategy, which takes a deferred retry context and
      returns a deferred retry context, deferred with an exception, or a
      synchronous exception. In the error case, retrying stops and the
      exception is passed on to the deferred returned by this fn. Otherwise,
      continues execution with the given retry context.

  Returns a deferred wrapping the results of `f`."
  [f strategy]
  (md/loop [ctx {:failures []}]
    (md/catch
     (f)
     (fn [e]
       (let [ctx (update ctx :failures conj e)]
         (md/chain (strategy ctx) md/recur))))))

(defn retry-exp-backoff
  "Takes a function that returns a `manifold.deferred/deferred`. Retries that
  function until it succeeds or the number of failures equal the stop value.

  Expects to encounter exceptions when retrying `f`. As such,
  it will catch all exceptions that `f` might throw and continue retrying.

  f - a function that should be retried; must return a
      `manifold.deferred/deferred'
  p - an int representing the initial number of seconds to wait before retrying.
      This will grow exponentially for each attempt.
  stop - an int representing the number of tries that the api should make before
         giving up and returning the last exception encountered.

  Returns a deferred wrapping the results of `f`."
  [f p stop]
  (retry f (comp (exponentially p) (up-to stop))))
