package com.logscale.agent.engine.proc;

import com.logscale.agent.engine.Engine;
import com.logscale.agent.event.*;
import com.logscale.agent.util.PushStream;
import com.logscale.agent.ws.WebsocketClientLoop;
import com.logscale.logger.Logger;
import io.netty.channel.*;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.net.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class WebsocketProcessor implements SourceProcessor {
    private static final Logger log = Logger.getLogger();

    private final URI url;

    private Engine engine;
    public WebsocketProcessor(String url) {
        try {
            this.url = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("bad url: " + url, e);
        }
    }

    @Override
    public void init(Engine engine) {
        this.engine = engine;
    }

    @Override
    public Stream<Event> events() {
        PushStream<String> stream = new PushStream<>(1000);
        WebsocketClientLoop loop = new WebsocketClientLoop(url) {
            @Override
            protected ChannelHandler handler() {
                return new Handler(stream);
            }
        };
        ExecutorService executorService = Executors.newSingleThreadExecutor(engine.threadFactory);
        executorService.submit(loop);
        EventSource eventSource = new EventSource(String.format("websocket[%s]", url), stream);
        return new EventBuffer(eventSource) {
            @Override
            public void close() {
                super.close();
                executorService.shutdownNow();
                stream.close();
            }
        };
    }

    private static final class Handler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
        private final Consumer<String> consumer;

        public Handler(Consumer<String> consumer) {
            this.consumer = consumer;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            super.channelActive(ctx);
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, TextWebSocketFrame messageFrame) throws Exception {
            String text = messageFrame.text();
            log.info("got message from %s: %s", ctx.channel().remoteAddress(), text);
            consumer.accept(text);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.error("exception caught in handler", cause);
            ctx.close();
        }
    }
}
