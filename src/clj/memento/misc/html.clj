(ns memento.misc.html
  (require [clojure.string :as string]
           [com.rpl.specter :refer [transform multi-path]])
  (import (org.jsoup Jsoup)))


(defn remove-html
  "Cleans HTML tags from a string while preserving new lines.

  Returns nil if it receives a nil value."
  [^String s]
  (when s
    (let [no-nl (string/replace s #"\n" "\\\\n")
          clean (.text (Jsoup/parse no-nl))]
      (string/trim (string/replace clean #"\\n" "\n")))))

(defn remove-html-from-vals
  "Removes the HTML from several values on a hashmap.
  
  Probably best suited for a memento.utils, but we don't have one of those yet"
  [item & fields]
  (transform (apply multi-path fields) remove-html item))