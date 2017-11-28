(ns k8s.jetty-test
  (:require [k8s.jetty :as sut]
            [clojure.test :refer :all]))


(deftest jetty-test


  (def cl (sut/client {:insecure? true}))




  )

