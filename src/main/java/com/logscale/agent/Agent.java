package com.logscale.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.logscale.agent.ws.WebsocketClient;
import com.logscale.logger.Logger;
import com.logscale.mixins.ToYaml;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Agent implements ToYaml, Runnable {
    private static final Logger log = Logger.getLogger();

    private static final int ENGINE_TERMINATE_TIMEOUT_SECONDS = 10;

    private final ThreadGroup threadGroup = new ThreadGroup("engine");
    private final AtomicInteger threadNumber = new AtomicInteger();
    private final ThreadFactory threadFactory = r -> new Thread(threadGroup, r, threadGroup.getName() + "-" + threadNumber.incrementAndGet());

    public  final ExecutorService executor = Executors.newCachedThreadPool(threadFactory);

    public String name;
    public String endpoint;
    public String key;

    public JsonNode config;

    private WebsocketClient client;

    public void start() {
        if (executor.isShutdown()) {
            throw new IllegalStateException("can't restart an agent");
        }
        executor.submit(this);
    }

    public void stop() {
        if (executor.isShutdown()) {
            log.warn("agent already stopped");
            return;
        }
        log.info("shutting down now");
        client.stop();
        executor.shutdownNow();
        try {
            log.info("waiting for termination");
            if (executor.awaitTermination(10, TimeUnit.SECONDS)) {
                log.info("agent terminated");
            } else {
                log.error("agent not terminated within timeout: " + ENGINE_TERMINATE_TIMEOUT_SECONDS);
            }
        } catch (InterruptedException e) {
            log.warn("interrupted while waiting for termination", e);
        }
    }

    @Override
    public String toString() {
        return toYaml();
    }

    @Override
    public void run() {
        log.info("running agent");
        client = new WebsocketClient(this, endpoint);
        client.start(this);
        long nextHeartbeat = System.currentTimeMillis() + 30000;
        while (!executor.isShutdown()) {
            if (System.currentTimeMillis() >= nextHeartbeat) {
                log.debug("heartbeat");
                nextHeartbeat = System.currentTimeMillis() + 30000;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                log.warn("interrupted", e);
                executor.shutdown();
                break;
            }
        }
        log.info("agent stopped");
    }
}
