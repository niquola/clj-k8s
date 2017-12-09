(ns k8s.jetty
  (:import [java.net URLEncoder]
           [java.util Base64]
           [java.nio ByteBuffer]
           [org.eclipse.jetty.client HttpClient]
           [org.eclipse.jetty.websocket.api WebSocketListener Session WriteCallback RemoteEndpoint]
           [org.eclipse.jetty.websocket.client WebSocketClient ClientUpgradeRequest]
           [org.eclipse.jetty.util.ssl SslContextFactory]

           [org.eclipse.jetty.client.api.Response]

           [org.eclipse.jetty.http MetaData HttpFields HttpURI HttpVersion]
           [org.eclipse.jetty.util FuturePromise Callback]
           [java.util.concurrent TimeUnit]

           [java.nio.charset Charset])
  (:require [clojure.string :as str]
            [cheshire.core :as json]
            [clojure.java.io :as io]))

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


(defn read-lines [s]
  (let [parts (str/split s #"\n")]
    (if (str/ends-with? s "\n")
      [parts nil]
      [(butlast parts) (last parts)])))

(read-lines "aaaa\nbbbb\nc")
(read-lines "aaaa\nbbbb\n")

(defn buf->str [buf]
  (let [ba (byte-array (.remaining buf))]
    (.get buf ba)
    (String. ba "UTF-8")))


(defn http-stream-listener
  [on-message on-complete]
  (let [tail (atom nil)]
    (proxy
        [org.eclipse.jetty.client.api.Response$Listener$Adapter] []
      ;; onComplete(Result result)
      (onComplete [res]
        (println "on complete" res)
        (on-complete res))

      ;; Response response, java.nio.ByteBuffer content
      (onContent [resp ^java.nio.ByteBuffer content cb]
        ;; (println "onContent" resp content)
        (let [s (str @tail (buf->str content))
              [lines t] (read-lines s)]
          (doseq [l lines] (on-message l))
          (reset! tail t))
        (.succeeded cb)))))


(defn request-stream
  [^HttpClient client
   {uri :uri
    params :params
    on-complete :on-complete
    on-message :on-message
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

    (.send req (http-stream-listener on-message on-complete))
    req))

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


(comment

  (def http (client {:insecure? true}))
  (.stop http)

  (.start http)

  (def host "https://192.168.99.100:8443")

  (def host "http://localhost:8001")

  (def uri (str host "/api/v1/pods?watch=true"))

  "https://<api-server>/api/v1/namespaces/default/pods/pg3-cleo-master-lightseagreen-363763885-0mbfz"

  (def http (client {:insecure? true}))

  (def rss
    (request-stream http
                    {:uri uri 
                     :method "GET"
                     :on-complete (fn [res] (println "STATUS:" (.getStatus res)))
                     :on-message (fn [msg] (println "MESS:" msg))
                     :headers {
                               ;;"Authorization" (str "Bearer " token)
                               }}))

  (def result
    (-> (.newRequest http uri)
        (.method "GET")
        (.header "Authorization"
                 (str "Bearer " token))
        (.send)))
  
  rss

 result

  (.getStatus result)
  (.toString result)
  (.getBody result)

  (def cl (client {:insecure? true}))

  (def token "???")
  
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
                {:uri (str "wss://????/api/v1/namespaces/default/pods/pg3-cleo-master-lightseagreen-363763885-0mbfz/exec?"
                           "command=ls&command-lah"
                           "&container=pg&stderr=true&stdout=true")
                 :method "POST"
                 :headers {"Authorization" (str "Bearer " token)}}))


  wres

  (def watchres
    (ws-request wws
                {:uri (str "wss://???/api/v1/pods?watch=true")
                 :method "POST"
                 :headers {"Authorization" (str "Bearer " token)}}))

  watchres


  )
