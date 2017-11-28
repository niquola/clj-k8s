(ns k8s.info-test
  (:require [k8s.info :as sut]
            [matcho.core :as matcho]
            [clojure.test :refer :all]))

(deftest secrets-test
  (matcho/match
   (sut/resolve-secrets
    {} {:spec   {:prop {:valueFrom {:secretKeyRef {:name "Secrets" :key "Key1"}}}}
        :nested {:attr1 {:natr2 {:valueFrom {:secretKeyRef {:name "DB" :key "password"}}}
                         :natr3 {:valueFrom {:configMapKeyRef {:name "CM" :key "cfg"}}}}}
        :vector [{:prop {:valueFrom {:secretKeyRef {:name "Secrets" :key "Key1"}}}}
                 {:prop {:valueFrom {:secretKeyRef {:name "Secrets" :key "Key2"}}}}]})
   {:secretKeyRef {:Secrets {:Key1 [[:spec :prop] [:vector 0 :prop]]
                        :Key2 [[:vector 1 :prop]]}
                   :DB {:password [[:nested :attr1 :natr2]]}}
    :configMapKeyRef {:CM {:cfg [[:nested :attr1 :natr3]]}}}))

(def ctx {:base-url "http://locahost:8001"})

(deftest url-builder-test 

  (is (= (sut/build-url ctx {:kind "PersistentVolumeClaim" :apiVersion "v1"})
         "http://locahost:8001/api/v1/persistentvolumeclaims"))

  (is (= (sut/build-url ctx {:kind "PersistentVolumeClaim" :apiVersion "apiextensions.k8s.io/v1beta1"} {:labelSelector "system in (c3)"})
         "http://locahost:8001/apis/apiextensions.k8s.io/v1beta1/persistentvolumeclaims?labelSelector=system+in+%28c3%29"))

  (is (= (sut/build-url ctx {:kind "PersistentVolumeClaim" :apiVersion "v1" :ns "test"})
       "http://locahost:8001/api/v1/namespaces/test/persistentvolumeclaims"))

  (is (= (sut/build-url ctx {:kind "PersistentVolumeClaim" :apiVersion "v1" :ns "test"})
       "http://locahost:8001/api/v1/namespaces/test/persistentvolumeclaims"))

  )
