package com.logscale.agent.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.logscale.agent.event.Event;
import com.logscale.agent.ws.WebsocketClientSession;
import com.logscale.logger.Logger;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class Engine implements Runnable {
    private static final Logger log = Logger.getLogger();

    final String id;
    final WebsocketClientSession session;
    final JsonNode config;

    final List<Processor> processors;
    final List<Consumer<Event>> handlers;

    final ThreadGroup threadGroup = new ThreadGroup("engine");
    final AtomicInteger threadNumber = new AtomicInteger();
    final ThreadFactory threadFactory = r -> new Thread(threadGroup, r, threadGroup.getName() + "-" + threadNumber.incrementAndGet());

    public Engine(WebsocketClientSession session, JsonNode config) {
        log.info("constructing engine from config: %s", config);

        this.session = session;
        this.config = config;
        this.id = config.get("id").asText();

        JsonNode processorsNode = config.get("processors");
        if (processorsNode == null) {
            throw new IllegalArgumentException("config without processors: " + config);
        }

        processors = new ArrayList<>();
        handlers = new ArrayList<>();
        processorsNode.iterator().forEachRemaining(processorNode -> {
            ProcessorType processorType = ProcessorType.valueOf(processorNode.get("type").asText());
            Processor processor = processorType.create(processorNode);
            processors.add(processor);
            log.info("initializing new %s processor: %s", processorType.name(), processor);
            processor.init(this);
            Consumer<Event> handler = processor.handler();
            if (handler != null) {
                handlers.add(handler);
            }
        });
    }

    @Override
    public void run() {
        log.info("creating event stream futures");
        Stream<Future> eventsFutures = processors.parallelStream().map(this::makeFuture);
        log.info("waiting for event stream futures");
        eventsFutures.forEach(Engine::getFuture);
        log.info("got all event stream futures");
    }

    private Future makeFuture(Processor processor) {
        Runnable task = () -> {
            Stream<Event> events = processor.events();
            if (events == null) {
                return;
            }
            events.forEach(event -> {
                log.debug("event: %s", event);
                try {
                    session.send(event);
                    handlers.parallelStream().forEach((handler) -> handler.accept(event));
                } catch (IOException e) {
                    processor.events().close();
                    throw new UncheckedIOException("trouble sending event from processor " + processor + ": " + event, e);
                }
            });
        };
        return Executors.newSingleThreadExecutor(threadFactory).submit(task);
    }

    private static void getFuture(Future future) {
        try {
            future.get();
        } catch (Exception e) {
            log.error("trouble getting future", e);
        }
    }
}
