(ns banach.pagination
  (:require [manifold.stream :as ms]
            [manifold.deferred :as md]))

(defn paginated->stream
  "Given a resource with pagination semantics, turns it into a single
  stream that transparently handles pagination for you."
  [handle-todo first-todo get-next-todo get-results]
  (let [todo-stream (ms/stream 10)
        rsrc-stream (ms/stream 20)]
    (ms/put! todo-stream first-todo)
    (ms/connect-via
     todo-stream
     (fn [todo]
       (md/chain (handle-todo todo)
                 (fn [response]
                   (if-some [next-todo (get-next-todo response)]
                     (ms/put! todo-stream next-todo)
                     (ms/close! todo-stream))
                   (when-let [rs (get-results response)]
                     (ms/put-all! rsrc-stream rs)))))
     rsrc-stream)
    rsrc-stream))
