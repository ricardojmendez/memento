(ns memento.test.handler
  (:require [memento.handler :refer :all]
            [clojure.test :refer :all]
            [ring.mock.request :refer :all])
  (:use clojure.test
        ring.mock.request
        memento.handler))

(deftest test-app
  (testing "main route"
    (let [response ((app) (request :get "/"))]
      (is (= 200 (:status response)))))

  (testing "not-found route"
    (let [response ((app) (request :get "/invalid"))]
      (is (= 404 (:status response))))))
