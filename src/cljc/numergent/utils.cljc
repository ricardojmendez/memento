(ns numergent.utils
  (require [clojure.string :as string])
  (import (org.jsoup Jsoup)))

(defn in-seq? [s x]
  (some? (some #{x} s)))


(defn parse-string-number
  "Receives a string and parses it. If the string is nil or empty, it returns 0."
  [^String s]
  (cond
    (not-empty s) (read-string s)
    :else 0))


(defn remove-html
  "Cleans HTML tags from a string while preserving new lines"
  [^String s]
  (let [no-nl (string/replace s #"\n" "\\\\n")
        clean (.text (Jsoup/parse no-nl))]
    (string/replace clean #"\\n" "\n"))
  )