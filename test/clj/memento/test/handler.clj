(ns memento.test.handler
  (:require [memento.handler :refer :all]
            [clojure.test :refer :all]
            [ring.mock.request :refer :all]))

(deftest test-app
  (testing "Our general routes are setup"
    (are [status path] (= status (:status ((app) (request :get path))))
                       200 "/"
                       200 "/about"
                       200 "/record"
                       200 "/login"
                       200 "/signup"
                       200 "/remember"
                       404 "/thread"                        ; Thread expects an ID
                       200 "/thread/"                       ; ID can be null
                       200 "/thread/some-id"
                       ))

  (testing "not-found route"
    (let [response ((app) (request :get "/invalid"))]
      (is (= 404 (:status response))))))
