package com.logscale.agent.ws;

import com.fasterxml.jackson.databind.*;
import com.logscale.agent.engine.Engine;
import com.logscale.agent.ws.msg.AuthenticateMessage;
import com.logscale.logger.Logger;
import io.netty.channel.*;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.util.*;
import java.util.concurrent.*;

public class WebsocketClientHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private static final Logger log = Logger.getLogger();

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ConcurrentMap<String, List<ChannelHandlerContext>> subs = new ConcurrentHashMap<>();

    private final WebsocketClientSession session;

    public WebsocketClientHandler(WebsocketClientSession session) {
        this.session = session;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, TextWebSocketFrame messageFrame) throws Exception {
        String json = messageFrame.text();
        log.info("got message from %s: %s", ctx.channel().remoteAddress(), json);
        JsonNode m;
        try {
            m = objectMapper.readTree(messageFrame.text());
        } catch (Exception e) {
            log.error("unable to parse message frame from %s: %s", ctx.channel(), json);
            return;
        }
        if (!m.has("type")) {
            log.warn("discarding message from %s with no type: %s", ctx.channel(), m.toString());
            return;
        }
        MessageType type = MessageType.valueOf(m.get("type").asText());
        log.debug("channel %s received message of type %s: %s", ctx.channel(), type, m.toString());
        switch (type) {
            case startSession:
                log.info("authenticating session");
                new AuthenticateMessage(session.agent, m).send(ctx);
                break;
            case configureAgent:
                log.info("configuring agent");
                session.agent.executor.submit(new Engine(session, m.get("agent")));
                break;
            case uiSubscribe:
                String cmd = m.get("command").asText();
                List<ChannelHandlerContext> chans = new ArrayList<>();
                if (subs.putIfAbsent(cmd, Collections.synchronizedList(chans)) != null) {
                    chans = subs.get(cmd);
                }
                chans.removeIf(c -> !c.channel().isWritable());
                chans.add(ctx);
                log.info("subscribed %s to: %s", ctx.channel(), cmd);
                break;
            default:
                log.error("unhandled message type: %s", type);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("exception caught in websocket client handler", cause);
        ctx.close();
    }

    private static enum MessageType {
        startSession,
        configureAgent,
        uiSubscribe,
    }
}