(ns k8s.netty
  (:import
   [io.netty.bootstrap Bootstrap];
   [io.netty.channel.Channel];
   [io.netty.channel EventLoopGroup];
   [io.netty.channel.nio NioEventLoopGroup];
   [io.netty.channel.socket.nio NioSocketChannel];
   [io.netty.handler.codec.http DefaultFullHttpRequest];
   [io.netty.handler.codec.http HttpHeaderNames];
   [io.netty.handler.codec.http HttpHeaderValues];
   [io.netty.handler.codec.http HttpMethod];
   [io.netty.handler.codec.http HttpRequest];
   [io.netty.handler.codec.http HttpVersion];
   [io.netty.handler.codec.http.cookie ClientCookieEncoder];
   [io.netty.handler.codec.http.cookie DefaultCookie];
   [io.netty.handler.ssl SslContext];
   [io.netty.handler.ssl SslContextBuilder];
   [io.netty.handler.ssl.util InsecureTrustManagerFactory];

   [io.netty.channel ChannelInitializer];
   [io.netty.channel.ChannelPipeline];
   [io.netty.channel.socket SocketChannel];
   [io.netty.handler.codec.http HttpClientCodec];
   [io.netty.handler.codec.http HttpContentDecompressor];
   [io.netty.handler.ssl SslContext];

   [io.netty.channel ChannelHandlerContext];
   [io.netty.channel SimpleChannelInboundHandler];
   [io.netty.handler.codec.http HttpContent];
   [io.netty.handler.codec.http HttpUtil];
   [io.netty.handler.codec.http HttpObject];
   [io.netty.handler.codec.http HttpResponse];
   [io.netty.handler.codec.http LastHttpContent];
   [io.netty.util CharsetUtil];

   [io.netty.channel ChannelHandler ChannelHandlerContext ChannelInboundHandlerAdapter]
   ))


;;         if (!response.headers().isEmpty()) {
;;             for (CharSequence name: response.headers().names()) {
;;                 for (CharSequence value: response.headers().getAll(name)) {
;;                     System.err.println("HEADER: " + name + " = " + value);
;;                 }
;;             }
;;             System.err.println();
;;         }


(defn handler []
  (proxy [SimpleChannelInboundHandler] []

    (exceptionCaught [^ChannelHandlerContext ctx, ^Throwable cause]
      (.printStackTrace cause)
      (.close ctx))

    (channelRead0 [^ChannelHandlerContext ctx, ^HttpObject msg]
      (when (instance? HttpResponse msg)
        (let [^HttpResponse resp (cast HttpResponse msg)]
          (println (.status resp))
          (println (.protocolVersion resp))
          (if (HttpUtil/isTransferEncodingChunked resp)
            (println "Content Chunked {")
            (println "Content {"))))
      (when (instance? HttpContent msg)
        (let [^HttpContent cont (cast HttpContent msg)]
          (println
           (-> (.content cont)
               (.toString CharsetUtil/UTF_8))))

        (when (instance? LastHttpContent msg)
          (.close ctx))))))

(defn add-last [p h]
  (.addLast p (into-array ChannelHandler [h])))

;;// Uncomment the following line if you don't want to handle HttpContents.
;;//p.addLast(new HttpObjectAggregator(1048576));
(defn initializer [^SslContext ssl]
  (proxy [ChannelInitializer] []
    (initChannel [^SocketChannel ch]
      (let [p (.pipeline ch)]
        (when ssl
          (add-last p (.newHandler ssl (.alloc ch))))
        (doto p
          (add-last (HttpClientCodec.))
          (add-last (HttpContentDecompressor.))
          (add-last (handler)))))))


(comment
  (def group (NioEventLoopGroup.))

  (def b (Bootstrap.))

  (def ssl (-> (SslContextBuilder/forClient)
               (.trustManager InsecureTrustManagerFactory/INSTANCE)
               (.build)))

  (SslContext/newClientContext InsecureTrustManagerFactory/INSTANCE)

  (-> (.group b group)
      (.channel NioSocketChannel)
      (.handler (initializer nil #_ssl)))


  (def ch (-> (.connect b "127.0.0.1" 8001)
              (.sync)
              (.channel)))
  ch

  (.shutdownGracefully group)

  (def host "http://localhost:8001")

  (def uri (str host "/api/v1/pods?watch=true"))

  (def req (DefaultFullHttpRequest.
            HttpVersion/HTTP_1_1
            HttpMethod/GET
            uri))

  (doto (.headers req)
    (.set HttpHeaderNames/HOST, "127.0.0.1")
    (.set HttpHeaderNames/CONNECTION, HttpHeaderValues/CLOSE)
    (.set HttpHeaderNames/ACCEPT_ENCODING, HttpHeaderValues/GZIP))

  (seq (.headers req))

  req

  (.writeAndFlush ch req)

  ;;     ch.writeAndFlush(request);
  ;;     group

   ;;     Channel ch = b.connect(host, port).sync().channel();

   ;;     // Prepare the HTTP request.
   ;;     HttpRequest request = new DefaultFullHttpRequest(
   ;;             HttpVersion.HTTP_1_1, HttpMethod.GET, uri.getRawPath());
   ;;     request.headers().set(HttpHeaderNames.HOST, host);
   ;;     request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
   ;;     request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP);

   ;;     // Set some example cookies.
   ;;     request.headers().set(
   ;;             HttpHeaderNames.COOKIE,
   ;;             ClientCookieEncoder.STRICT.encode(
   ;;                     new DefaultCookie("my-cookie", "foo"),
   ;;                     new DefaultCookie("another-cookie", "bar")));

   ;;     // Send the HTTP request.
   ;;     ch.writeAndFlush(request);

   ;;     // Wait for the server to close the connection.
   ;;     ch.closeFuture().sync();
   ;; } finally {
   ;;     // Shut down executor threads to exit.
   ;;     group.shutdownGracefully();
   ;; }






  )
