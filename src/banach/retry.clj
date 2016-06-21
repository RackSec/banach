(ns banach.retry
  (:require
   [clojure.math.numeric-tower :as math]
   [manifold.deferred :as md]
   [manifold.time :as mt]))

(defn ^:private exponentially
  "Returns a strategy that causes an exponentially increasing wait before
  retrying."
  [wait]
  (fn [failures]
    (math/expt wait (count failures))))

(defn ^:private up-to
  [stop retry?]
  (fn [failures]
    (if (< (count failures) stop)
      (retry? failures)
      (throw (last failures)))))
  "Returns a strategy that allows up to n retries. "

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
  (md/loop [failures []]
    (md/catch
     (f)
     Exception
      (fn [exc]
        (let [all-failures (conj failures exc)
              wait (strategy all-failures)]
          (mt/in (mt/seconds wait) #(md/recur all-failures)))))))

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
  (retry f (->> (exponentially p)
                (up-to stop))))
