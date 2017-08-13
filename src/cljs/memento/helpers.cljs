(ns memento.helpers
  (:require [clojure.string :as string]
            [cljs-time.format :as tf]
            [cljs-time.coerce :as tc]
            [markdown.common :as mdcommon]
            [markdown.core :refer [md->html]]
            [markdown.transformers :as transformers]
            [markdown.lists :as mdlists]
            [markdown.links :as mdlinks]
            ))


(defn paragraph-on-single-line
  "Adds a <p> even when we're at the end of the file and the last line is empty, so that
  we consistently return lines wrapped in paragraph even if it's free-standing text.
  Replaces the default paragraph transformer."
  [text {:keys [eof heading hr code lists blockquote paragraph last-line-empty?] :as state}]
  (cond
    (or heading hr code lists blockquote)
    [text state]

    paragraph
    (if (or eof (empty? (string/trim text)))
      [(str (transformers/paragraph-text last-line-empty? text) "</p>") (assoc state :paragraph false)]
      [(transformers/paragraph-text last-line-empty? text) state])

    last-line-empty?
    [(str "<p>" text) (assoc state :paragraph true :last-line-empty? false)]

    :default
    [text state]))

;; Transformer vector. We are excluding headings, since we use the hash as tags.
(def md-transformers
  [transformers/empty-line
   transformers/codeblock
   transformers/code
   mdcommon/escaped-chars
   mdcommon/inline-code
   transformers/autoemail-transformer
   transformers/autourl-transformer
   mdlinks/link
   mdlinks/reference-link
   mdlists/li
   mdcommon/italics
   mdcommon/em
   mdcommon/strong
   mdcommon/bold
   mdcommon/strikethrough
   transformers/superscript
   transformers/blockquote-1
   transformers/blockquote-2
   paragraph-on-single-line                                 ; Replaces transformers/paragraph
   transformers/br
   mdcommon/thaw-strings
   ])



;;;;-------------------------
;;;; Helpers
;;;;-------------------------

(defn add-html-to-thoughts
  "Receives a list of thoughts and converts the markdown to html, adding it
  to the map as a :html attribute"
  [thoughts]
  (map #(assoc % :html (md->html (:thought %) :replacement-transformers md-transformers))
       thoughts))

(defn format-date
  "Formats a date for displaying"
  [d]
  (tf/unparse (tf/formatters :date-hour-minute)
              (tc/from-date d)))

