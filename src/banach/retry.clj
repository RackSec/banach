(ns banach.retry
  (:require
   [clojure.math.numeric-tower :as math]
   [manifold.deferred :as md]
   [manifold.time :as mt]))

(defn routing
  "Returns a strategy that routes to different strategies based on context
  predicates.

  Takes pairs of predicates and matching strategies. When the returned strategy
  is used, the predicates will be tried, in order; the context is then passed to
  the corresponding strategy of the matching predicate. For example:

  ```
  (routing a? give-up b? (exponentially 3))
  ```

  ... returns a strategy that when used, if `(a? ctx)` will delegate
  to [[give-up]], if instead `(b? ctx)` will delegate to that [[exponentially]]
  strategy, and if neither matches, will (synchronously) raise an exception.
  Because the resulting strategy will raise if no predicates match, many users
  will want a `(constantly true)` clause at the end to implement a default
  fallback strategy for unforeseen exceptions."
  [& pred-strat-pairs]
  (fn [d]
    (md/chain
     d
     (fn [{:keys [failures] :as ctx}]
       (if-some [strat (first (for [[pred strat] (partition 2 pred-strat-pairs)
                                    :when (pred ctx)]
                                strat))]
         (strat ctx)
         (throw (ex-info "no matching strat to route to" {})))))))

(defn give-up
  "A strategy that raises the most recent failure in the context."
  [d]
  (md/chain d (fn [{:keys [failures]}] (throw (last failures)))))

(defn fatal-ctx
  "Creates a strategy that bails (throws the most recent exception) if the
  context shows a fatal state (according to the given predicate).

  See also [[fatal-exception]]; the difference with this function is that that
  function takes a predicate on the exception instance, whereas this takes a
  predicate on the context."
  [is-fatal-ctx?]
  (routing
   is-fatal-ctx? give-up
   (constantly true) identity))

(defn fatal-exception
  "Creates a strategy that bails (throws the most recent exception) if it
  matches the given predicate.

  See also [[fatal-ctx]]; the difference with this function is that this
  function takes a predicate on the exception instance, whereas [[fatal-ctx]]
  takes a predicate on the context.
  "
  [is-fatal-exception?]
  (fatal-ctx (comp is-fatal-exception? last :failures)))

(defn exponentially
  "Returns a strategy that causes an exponentially increasing wait before
  retrying. The base wait is measured in seconds."
  [base-wait]
  (fn [d]
    (md/let-flow [{:keys [failures] :as ctx} d
                  delay-ms (->> (count failures)
                                (math/expt 2)
                                (* base-wait)
                                (mt/seconds))]
      (mt/in delay-ms #(md/success-deferred ctx)))))

(defn up-to
  "Returns a strategy that allows up to `stop` retries, otherwise
  raises the last exception."
  [stop]
  (fn [d]
    (md/let-flow [{:keys [failures] :as ctx} d]
      (if (< (count failures) stop)
        ctx
        (throw (last failures))))))

(defn retry
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
