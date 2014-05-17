package com.logscale.agent.ws;

import com.logscale.agent.Agent;
import com.logscale.logger.Logger;
import com.logscale.mixins.JsonMessage;
import io.netty.channel.ChannelHandler;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.*;

public class WebsocketClientSession extends WebsocketClientLoop {
    private static final Logger log = Logger.getLogger();

    private static final int STOP_TIMEOUT_SEC = 3;

    public final WebsocketClient client;
    public final Agent agent;

    private Future future;

    public WebsocketClientSession(WebsocketClient client, Agent agent) {
        super(URI.create(client.endpoint));
        this.client = client;
        this.agent = agent;
        if (!"wss".equals(endpoint.getScheme())) {
            throw new IllegalArgumentException("unsupported protocol (expected wss) for URI " + endpoint);
        }
    }

    public synchronized void start() {
        if (future != null) {
            log.warn("already started");
        } else {
            log.info("starting");
            future = agent.executor.submit(this);
        }
    }

    public synchronized void stop() {
        if (future == null) {
            log.warn("already stopped");
        } else {
            log.info("stopping");
            future.cancel(true);
            try {
                future.get(STOP_TIMEOUT_SEC, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("trouble stopping session", e);
            } finally {
                future = null;
            }
        }
    }

    public void send(JsonMessage msg) throws IOException {
        log.debug("sending message: %s", msg);
        msg.send(ch);
    }

    @Override
    protected ChannelHandler handler() {
        return new WebsocketClientHandler(this);
    }
}
