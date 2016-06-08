(ns banach.test-utils
  (:require
   [taoensso.timbre :as timbre]))

(defn use-atom-log-appender!
  "Adds a log observer that saves its log messages to an atom.

  Returns an atom that wraps a vector of possible log messages"
  []
  (let [log (atom [])
        log-appender-fn (fn [data]
                          (let [{:keys [output-fn]} data
                                formatted-output-str (output-fn data)]
                            (swap! log conj formatted-output-str)))]
    (timbre/merge-config!
     {:appenders
      {:atom-appender
       {:async false
        :enabled? true
        :min-level nil
        :output-fn :inherit
        :fn log-appender-fn}}})
    log))
