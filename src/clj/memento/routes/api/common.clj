(ns memento.routes.api.common
  (:require [cognitect.transit :as transit]))

(defn read-content
  "Receives a request context and returns its contents"
  [ctx]
  (let [reader (transit/reader (get-in ctx [:request :body]) :json)]
    (transit/read reader)))