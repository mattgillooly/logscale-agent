package com.logscale.agent.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.logscale.agent.event.Event;
import com.logscale.agent.ws.WebsocketClientSession;
import com.logscale.agent.ws.msg.EventMessage;
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
    final EventBus bus;

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

        JsonNode busNode = config.get("bus");
        EventBusType eventBusType;
        if (busNode != null) {
            eventBusType = EventBusType.valueOf(busNode.get("type").asText());
        } else {
            eventBusType = EventBusType.naive;
        }
        bus = eventBusType.create(busNode);

        bus.register("api", new Consumer<Event>() {
            @Override
            public void accept(Event event) {
                log.debug("sending event: %s", event);
                try {
                    session.send(new EventMessage(event));
                } catch (IOException e) {
                    throw new UncheckedIOException("trouble sending event", e);
                }

            }
        });

        processorsNode.iterator().forEachRemaining(this::initProcessor);
    }

    private void initProcessor(JsonNode processorNode) {
        ProcessorType processorType = ProcessorType.valueOf(processorNode.get("type").asText());
        if (processorNode.get("disabled") != null && processorNode.get("disabled").asBoolean()) {
            log.info("skipping disabled processor of type: %s", processorType.name());
            return;
        }
        Processor processor = processorType.create(processorNode);
        processors.add(processor);
        log.info("initializing new %s processor: %s", processorType.name(), processor);
        processor.init(this);
        Consumer<Event> handler = processor.handler();
        if (handler != null) {
            bus.register(processorType.name(), handler);
        }
    }

    @Override
    public void run() {
        log.info("starting bus");
        bus.start(this);
        log.info("creating event stream futures");
        Stream<Future> eventsFutures = processors.parallelStream().map(this::makeFuture);
        log.info("waiting for event stream futures");
        eventsFutures.forEach(Engine::getFuture);
        log.info("stopping bus");
        bus.stop();
    }

    private Future makeFuture(Processor processor) {
        Runnable task = () -> {
            Stream<Event> events = processor.events();
            if (events == null) {
                return;
            }
            events.forEach(event -> {
                log.debug("event: %s", event);
                bus.push(event);
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
