package com.logscale.agent.ws;

import com.logscale.agent.Agent;
import com.logscale.logger.Logger;
import com.logscale.mixins.ToYaml;

public class WebsocketClient implements ToYaml {
    private static final Logger log = Logger.getLogger();

    public final Agent agent;
    public final String endpoint;

    private WebsocketClientSession session;

    public WebsocketClient(Agent agent, String endpoint) {
        this.agent = agent;
        this.endpoint = endpoint;
    }

    public synchronized void start(Agent agent) {
        if (session != null) {
            log.warn("start called with a live session");
            return;
        }
        log.info("starting websocket client session");
        session = new WebsocketClientSession(this, agent);
        session.start();
    }

    public synchronized void stop() {
        if (session == null) {
            log.warn("stop called with no live session");
            return;
        }
        log.info("stopping websocket client session");
        session.stop();
        session = null;
    }
}
