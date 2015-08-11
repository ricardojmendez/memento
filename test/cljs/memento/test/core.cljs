(ns memento.test.core
  (:require [cljs.test :refer-macros [deftest testing is]]
            [memento.core :as core]))


;
; Trope processor tests
;

(deftest is-true
  (println "Hello world")
  (is (= true true)))

