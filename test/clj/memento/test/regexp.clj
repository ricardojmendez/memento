(ns memento.test.regexp
  (:use clojure.test))


(deftest regexp-exploration
  ; Testing with http://www.regexr.com/
  (are [line matches] (= matches (re-seq #"#+[\w\-\+]+" line))
                      "#hello world" ["#hello"]
                      "hello ##double 1999 #with9numbers" ["##double" "#with9numbers"]
                      "a #star-crossed tale of #double+good lovers" ["#star-crossed" "#double+good"]
    ))
