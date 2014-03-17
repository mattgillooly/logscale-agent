package com.logscale.agent.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.logscale.agent.ws.WebsocketClientSession;
import com.logscale.logger.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class Engine implements Runnable {
    private static final Logger log = Logger.getLogger();

    final String id;
    final WebsocketClientSession session;
    final JsonNode config;

    final List<Tail> tails;

    final ThreadGroup threadGroup = new ThreadGroup("engine");
    final AtomicInteger threadNumber = new AtomicInteger();
    final ThreadFactory threadFactory = r -> new Thread(threadGroup, r, threadGroup.getName() + "-" + threadNumber.incrementAndGet());

    public Engine(WebsocketClientSession session, JsonNode config) {
        this.session = session;
        this.config = config;
        this.id = config.get("id").asText();

        tails = new ArrayList<>();
        config.get("sources").iterator().forEachRemaining(source -> {
            if (source.get("type").asText().equals("file")) {
                Tail tail = new Tail(Engine.this, source.get("path").asText());
                tails.add(tail);
                log.info("configured new tail: %s", tail.path);
            }
        });
    }

    @Override
    public void run() {
        Stream<Future> futures = tails.stream().map(this::launchTail);
        futures.forEach(Engine::getFuture);
    }

    private Future launchTail(Tail tail) {
        return Executors.newSingleThreadExecutor(threadFactory).submit(tail);
    }

    private static void getFuture(Future future) {
        try {
            future.get();
        } catch (Exception e) {
            log.error("trouble getting future", e);
        }
    }
}
