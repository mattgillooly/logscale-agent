package com.logscale.agent.ws;

import com.logscale.agent.util.Ssl;
import com.logscale.logger.Logger;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.ssl.SslHandler;

import java.net.URI;
import java.util.concurrent.TimeUnit;

public abstract class WebsocketClientLoop implements Runnable {
    private static final Logger log = Logger.getLogger();

    private static final int CONNECT_TIMEOUT_SEC = 10;
    private static final int HANDSHAKE_TIMEOUT_SEC = 10;
    private static final int[] RECONNECT_DELAYS_SEC = {1, 2, 5, 10, 20, 30};
    private static final int RECONNECT_AFTER_HANDSHAKE_MIN_SEC = 10;

    protected final URI endpoint;

    protected volatile Channel ch;

    public WebsocketClientLoop(URI endpoint) {
        this.endpoint = endpoint;
    }

    protected abstract ChannelHandler handler();

    @Override
    public void run() {
        int reconnectAttempt = 0;
        while (!Thread.interrupted()) {
            log.info("stream started (%s)", reconnectAttempt);
            NioEventLoopGroup group = new NioEventLoopGroup();
            ch = null;

            try {
                WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(endpoint, WebSocketVersion.V13, null, false, new DefaultHttpHeaders());
                WebSocketClientProtocolHandler protocolHandler = new WebSocketClientProtocolHandler(handshaker);

                Bootstrap bootstrap = new Bootstrap();
                bootstrap.group(group).channel(NioSocketChannel.class).handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        if ("wss".equals(endpoint.getScheme())) {
                            pipeline.addLast("ssl", new SslHandler(Ssl.createSslEngine()));
                        }
                        // pipeline.addLast("logger", new io.netty.handler.logging.LoggingHandler(WebsocketClient.class, io.netty.handler.logging.LogLevel.WARN));
                        pipeline.addLast("http", new HttpClientCodec());
                        pipeline.addLast("aggregator", new HttpObjectAggregator(8192));
                        pipeline.addLast("protocol", protocolHandler);
                        pipeline.addLast("handler", handler());
                    }
                });

                if (reconnectAttempt > 0) {
                    int delaySec = RECONNECT_DELAYS_SEC[Integer.min(reconnectAttempt - 1, RECONNECT_DELAYS_SEC.length - 1)];
                    log.info("delaying reconnect attempt #%s for %ss", reconnectAttempt, delaySec);
                    Thread.sleep(TimeUnit.SECONDS.toMillis(delaySec));
                }
                reconnectAttempt++;
                log.info(() -> "connecting to " + endpoint);
                int port = endpoint.getPort();
                port = port == -1 ? 443: port;
                ChannelFuture cf = bootstrap.connect(endpoint.getHost(), port);
                if (!cf.await(TimeUnit.SECONDS.toMillis(CONNECT_TIMEOUT_SEC))) {
                    cf.cancel(true);
                    continue;
                }
                ch = cf.sync().channel();
                log.info("connected to " + ch.remoteAddress());
                //noinspection CodeBlock2Expr
                ch.closeFuture().addListener(
                        (io.netty.util.concurrent.Future<Void> future) -> {
                            log.warn("channel closed");
                        }
                );
                reconnectAttempt = 0;
                log.info("waiting for handshake");
                long handshakeTimeoutTs = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(HANDSHAKE_TIMEOUT_SEC);
                while (!handshaker.isHandshakeComplete() && System.currentTimeMillis() < handshakeTimeoutTs) {
                    Thread.sleep(100);
                }
                if (!handshaker.isHandshakeComplete()) {
                    log.warn(() -> "handshake did not complete within timeout (" + HANDSHAKE_TIMEOUT_SEC + "s), closing");
                    continue;
                }
                log.info("handshake complete");

                long afterHandshakeMillis = -System.currentTimeMillis();
                while (ch.isActive()) {
                    Thread.sleep(500);
                }
                log.warn("channel no longer active");
                afterHandshakeMillis += System.currentTimeMillis();
                long waitAfterHandshakeMillis = TimeUnit.SECONDS.toMillis(RECONNECT_AFTER_HANDSHAKE_MIN_SEC) - afterHandshakeMillis;
                if (waitAfterHandshakeMillis > 0) {
                    log.warn("waiting up to %s seconds from handshake after quick channel disconnect (%s millis left)", RECONNECT_AFTER_HANDSHAKE_MIN_SEC, waitAfterHandshakeMillis);
                    Thread.sleep(waitAfterHandshakeMillis);
                }

            } catch (Exception e) {
                log.error("error in stream", e);
            } finally {
                if (ch != null) {
                    ch.close();
                    ch = null;
                }
                group.shutdownGracefully();
            }
        }
    }
}
