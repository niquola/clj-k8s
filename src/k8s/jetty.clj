(ns k8s.jetty
  (:import [java.net URLEncoder]
           [java.util Base64]
           [java.nio ByteBuffer]
           [org.eclipse.jetty.client HttpClient]
           [org.eclipse.jetty.websocket.api WebSocketListener Session WriteCallback RemoteEndpoint]
           [org.eclipse.jetty.websocket.client WebSocketClient ClientUpgradeRequest]
           [org.eclipse.jetty.util.ssl SslContextFactory]
           [org.eclipse.jetty.http2.api Stream]
           [org.eclipse.jetty.http2.api.server ServerSessionListener]
           [org.eclipse.jetty.http2.frames DataFrame HeadersFrame]

           [org.eclipse.jetty.http MetaData HttpFields HttpURI HttpVersion]
           [org.eclipse.jetty.util FuturePromise Callback]
           [java.util.concurrent TimeUnit]
           [org.eclipse.jetty.http2.client HTTP2Client]

           [java.nio.charset Charset])
  (:require [clojure.string :as str]
            [cheshire.core :as json]))

(defn ssl-context []
  (let [sec (SslContextFactory.)]
    (.setTrustAll sec true)
    (.setValidateCerts sec false)
    (.start sec)
    sec))

(defonce ssl-ctx (atom nil))

(defn ensure-ssl-context []
  (if-let [ctx @ssl-ctx]
    ctx
    (reset! ssl-ctx (ssl-context))))


(defn client [{insecure? :insecure?}]
  ;; httpClient.setFollowRedirects(false);
  (let [cl (if insecure?
             (HttpClient. (ensure-ssl-context))
             (HttpClient.))]
    (.start cl)
    cl))

(defn parse-body [fmt body]
  (cond
    (and (= fmt "application/json") body)
    (json/parse-string body keyword)
    :else body))

(defn request
  [^HttpClient client
   {uri :uri
    params :params
    headers :headers
    method :method :as opts}]

  (let [req (.newRequest client uri)]
    (.method req (or "GET" method))

    (when params
      (doseq [[k v] params]
        (.param req (name k) v)))

    (when headers
      (doseq [[k v] headers]
        (.header req (name k) v)))

    (let [resp (.send req)
          headers (reduce (fn [acc field]
                            (assoc acc (keyword (.getName field)) (.getValue field)))
                          {} (.getHeaders resp))
          body (.getContentAsString resp)]
      {:status (.getStatus resp)
       :headers headers
       :body (parse-body (:Content-Type headers) body)})))

(defn ws-client [{insecure? :insecure?}]
  (let [cl (if insecure?
             (WebSocketClient. (ensure-ssl-context))
             (WebSocketClient.))]
    (.start cl)
    cl))

(def utf (Charset/forName "utf-8"))

(defn ws-listener
  []
  (let [p (promise)
        sb (StringBuilder.)]
    [p (reify WebSocketListener
       (onWebSocketConnect [this session]
         (println "Session" session))

       (onWebSocketText [this message]
         (println "on text not impl" message))

       (onWebSocketBinary [this bytes offset len]
         ;; (println "msg:" :len len :off offset (String. bytes))
         (.append sb (String. bytes)))

       (onWebSocketError [this cause]
         (deliver p {:error cause
                     :body (.toString sb)}))

       (onWebSocketClose [this status-code reason]
         (deliver p {:body (.toString sb)
                     :status status-code
                     :reason reason})))]))

(defn ws-request
  [^WebSocketClient client
   {uri :uri
    params :params
    headers :headers
    method :method :as opts}]

  (let [req (ClientUpgradeRequest.)]
    (.setMethod req (or "GET" method))

    (when params
      (doseq [[k v] params]
        (.setParam req (name k) v)))

    (when headers
      (doseq [[k v] headers]
        (.setHeader req (name k) v)))

    (let [[p listener]  (ws-listener)]
      (.connect client listener (java.net.URI. uri) req)
      @p)))


(defn http2-client
  [{insecure? :insecure?
    host :host
    port :port}]
  (let [cl (HTTP2Client.)]
    (.start cl)
    cl))

(defn http2-listener []
  (let [p (promise)
        sb (StringBuilder.)]
    [p (proxy [org.eclipse.jetty.http2.api.Stream$Listener$Adapter] []
         (onData [^Stream stream
                  ^DataFrame frame
                  ^Callback callback]
           (let [bs (byte-array (-> frame (.getData) (.remaining)))];)])
             (-> frame (.getData) (.get bs))
             (println "Data:" (String. bs))
             (.succeeded callback))))]))

(defn http2-request [client {uri :uri
                             host :host
                             port :port
                             method :method}]
  (let [method  (or "GET" method)
        huri    (HttpURI. uri)
        fields  (HttpFields.)
        req     (org.eclipse.jetty.http.MetaData$Request.
                 ^String method
                 ^HttpURI huri
                 HttpVersion/HTTP_2
                 ^HttpFields fields)
        headers (HeadersFrame. req nil true)
        [p listener] (http2-listener)
        pr (FuturePromise.)]
    (.connect client
              ;; ^SSLContextFactory (ensure-ssl-context)
              ^java.net.InetSocketAddress (java.net.InetSocketAddress. host (or port 80))
              (org.eclipse.jetty.http2.api.server.ServerSessionListener$Adapter.)
              ^FuturePromise pr)
    (let [session (.get pr 5 TimeUnit/SECONDS)]
      (.newStream session headers (FuturePromise.) listener)
      session)))

(comment

  (.start http)

  (def result
    (-> (.newRequest http "https://<api-server>/api/v1/namespaces/default/pods/pg3-cleo-master-lightseagreen-363763885-0mbfz")
        (.method "GET")
        (.header "Authorization"
                 "Bearer <token>")
        (.send)))

  (.getStatus result)
  (.toString result)
  (.getBody result)

  (def cl (client {:insecure? true}))

  (def token "????")
  (def api-server "????")

  (def rs (request cl
             {:uri "https://<api-server>/api/v1/namespaces/default/pods/pg3-cleo-master-lightseagreen-363763885-0mbfz"
              :method "GET"
              :headers {"Authorization" (str "Bearer " token)}}))
  (:body rs)

  (request cl
           {:uri "https://<api-server>/api/v1/namespaces/xxx/pods/pg3-cleo-master-lightseagreen-363763885-0mbfz"
            :method "GET"
            :headers {"Authorization" (str "Bearer " token)}})

  (:headers rs)
  (:body rs)

  (def wws (ws-client {:insecure? true}))
  (def wres
    (ws-request wws
                {:uri (str "wss://<api-server>/api/v1/namespaces/default/pods/pg3-cleo-master-lightseagreen-363763885-0mbfz/exec?"
                           "command=ls&command-lah"
                           "&container=pg&stderr=true&stdout=true")
                 :method "POST"
                 :headers {"Authorization" (str "Bearer " token)}}))

  wres



  (def http2 (http2-client {:insecure? true :host "localhost" :port 8001}))

  http2

  (def s
    (http2-request http2 {:uri "http://localhost:8001/api/v1/pods?watch=true"
                          :host "localhost"
                          :port 8001
                          :method "GET"}))

  s



  )
