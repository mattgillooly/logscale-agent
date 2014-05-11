package com.logscale.agent.engine;

import com.google.common.cache.*;
import com.google.common.collect.*;
import com.logscale.agent.event.Event;
import com.logscale.agent.ws.msg.EventMessage;
import com.logscale.logger.Logger;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public abstract class EventBus {
    private static final Logger log = Logger.getLogger();

    protected final BiMap<String, Consumer<Event>> handlers = HashBiMap.create();

    protected final AtomicReference<Engine> engineRef = new AtomicReference<>();

    protected final Cache<String, Event> eventCache = CacheBuilder.newBuilder().
            maximumSize(10000).
            expireAfterWrite(10, TimeUnit.MINUTES).
            build();

    public void register(String handlerName, Consumer<Event> handler) {
        if (handlers.containsKey(handlerName)) {
            throw new IllegalArgumentException("bus already contains handler named: " + handlerName);
        }
        handlers.put(handlerName, handler);
    }

    protected void handleEventId(String id) {
        Event event;
        try {
            event = eventCache.get(id, () -> {
                // TODO: cache miss, getting from api not yet implemented
                throw new NotImplementedException();
            });
        } catch (ExecutionException e) {
            throw new RuntimeException("trouble getting event id " + id + " from cache", e);
        }
        handleEvent(event);
    }

    protected void handleEvent(Event event) {
        handlers.values().parallelStream().forEach((handler) -> {
            String handlerName = handlers.inverse().get(handler);
            try {
                handler.accept(event);
            } catch (Exception e) {
                log.error("trouble handling event in '" + handlerName + "': " + event.id + "\n", e);
            }
        });

    }

    public void start(Engine engine) {
        engineRef.set(engine);
    }

    public void stop() {
        engineRef.set(null);
    }

    public boolean isStopped() {
        return engineRef.get() == null;
    }

    public void push(Event event) {
        eventCache.put(event.id, event);
        log.debug("sending event id %s to api: %s", event.id, event);
        try {
            engineRef.get().session.send(new EventMessage(event));
        } catch (IOException e) {
            throw new UncheckedIOException("trouble sending event", e);
        }
    }
}
