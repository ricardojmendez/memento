#!/usr/local/bin/planck
(ns memento.deploy.core
  (:require [goog.i18n.DateTimeFormat :as dtf]
            [planck.core :refer [exit]]
            [planck.shell :refer [sh]]))

(def format-map
  (let [f goog.i18n.DateTimeFormat.Format]
    {:FULL_DATE (.-FULL_DATE f)
    :FULL_DATETIME (.-FULL_DATETIME f)
    :FULL_TIME (.-FULL_TIME f)
    :LONG_DATE (.-LONG_DATE f)
    :LONG_DATETIME (.-LONG_DATETIME f)
    :LONG_TIME (.-LONG_TIME f)
    :MEDIUM_DATE (.-MEDIUM_DATE f)
    :MEDIUM_DATETIME (.-MEDIUM_DATETIME f)
    :MEDIUM_TIME (.-MEDIUM_TIME f)
    :SHORT_DATE (.-SHORT_DATE f)
    :SHORT_DATETIME (.-SHORT_DATETIME f)
    :SHORT_TIME (.-SHORT_TIME f)}))

(defn format-date-generic
  "Format a date using either the built-in goog.i18n.DateTimeFormat.Format 
  enum or a formatting string like \"dd MMMM yyyy\""
  [date-format date]
  (.format (goog.i18n.DateTimeFormat.
            (or (format-map date-format) date-format))
           (js/Date. date)))


(defn ensure-succeeded! [x]
  (print (:out x))
  (when-not (zero? (:exit x))
    (println "Non-zero exit code! There may be unstaged changes.")
    (print (:err x))
    (exit (:exit x))))


(defn do! [& args]
  (ensure-succeeded! (apply sh args)))

(def timestamp (format-date-generic "yyyyMMddHHmm" (js/Date.)))

; TODO: Once we get planck 1.6, add command line args so that we can do either
; release or start

(do! "git" "flow" "release" "start" timestamp)

(println "Done")