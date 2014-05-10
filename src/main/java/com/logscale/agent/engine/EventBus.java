package com.logscale.agent.engine;

import com.google.common.collect.*;
import com.logscale.agent.event.Event;
import com.logscale.logger.Logger;

import java.util.function.Consumer;

public abstract class EventBus {
    private static final Logger log = Logger.getLogger();

    protected final BiMap<String, Consumer<Event>> handlers = HashBiMap.create();

    public void register(String handlerName, Consumer<Event> handler) {
        if (handlers.containsKey(handlerName)) {
            throw new IllegalArgumentException("bus already contains handler named: " + handlerName);
        }
        handlers.put(handlerName, handler);
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
    }

    public void stop() {
    }

    public abstract void push(Event event);
}
