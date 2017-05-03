# Banach

[![Build Status](https://travis-ci.org/RackSec/banach.svg?branch=master)](https://travis-ci.org/RackSec/banach)
[![codecov](https://codecov.io/gh/RackSec/banach/branch/master/graph/badge.svg)](https://codecov.io/gh/RackSec/banach)
[![Clojars Project](https://img.shields.io/clojars/v/banach.svg)](https://clojars.org/banach)


This contains utilities that can be useful when working with [Manifold](https://github.com/ztellman/manifold).

## Retries

Banach provides flexible tools for doing retries of functions that can fail. By
default, it offers composable exponential backoff, maximum number of attempts,
and hard failures (that stop future retry attempts).

Minimum viable snippet (with the `banach.retry` namespace aliased as `r`):

```clojure
(r/retry f (comp (r/exponentially 3) (r/up-to 5)))
```

... which will return the result of calling a potentially deferred-returning
function `f`, retrying on errors, up to 5 times, with an exponential delay
(starting at 3 seconds, then waiting 3*(2 ^ n)) for each subsequent failure.

Maybe you know that a particular exception means you should stop trying, e.g.
because of an access control misconfiguration:

```clojure
(r/retry f
  (comp
    (r/exponentially 2)
    (r/up-to 5)
    (r/fatal-exception (fn [e] (= (.getMessage e) "KABOON")))))
```

(Note that the fatal exception check should probably come last in the `comp`:
you wouldn't want to potentially back off for some number of seconds for an
exception you know you'll never recover from anyway.)

Deep down, this functionality is built on a concept called "strategies", which
take a (potentially deferred) context and return a (potentially deferred)
context. Because a context is a (deferred) extensible map this lets you
implement pretty much any retry strategy you want in a composable way. By
default, contexts contain a vector of failures (exceptions) encountered so far.
So, for example, if you knew that seeing one particular exception after another
particular exception warranted special behavior, you can easily do that, too.

Other functions you may want to check out: `fatal-ctx` (like `fatal-exception`
but takes a predicate on the context instead of the most recent exception
`routing` (conditionally delegate to different strategies),), and pretty much the
entire `banach.retry` namespace.

## License

Copyright © Rackspace

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
