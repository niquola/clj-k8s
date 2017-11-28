(defproject k8s "0.1.0-SNAPSHOT"
  :description "k8s client for clojure"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha16"]
                 [cheshire "5.7.1"]
                 [clj-json-patch "0.1.4"]
                 [org.eclipse.jetty.http2/http2-client "9.4.7.v20170914"]
                 [org.eclipse.jetty.websocket/websocket-client "9.4.7.v20170914"]
                 [hiccup "1.0.5"]
                 [ch.qos.logback/logback-classic "1.2.2"]
                 [http-kit "2.2.0"]
                 [clj-yaml "0.4.0"]
                 [clj-jwt "0.1.1"]
                 [clj-time "0.13.0"]
                 [inflections "0.13.0"]
                 [org.mortbay.jetty.alpn/alpn-boot "8.1.9.v20160720"]
                 [matcho "0.1.0-RC5"]])
