package com.logscale.agent.engine;

import com.logscale.agent.event.Event;

import java.util.function.Consumer;

public interface SourceProcessor extends Processor {
    @Override
    default public Consumer<Event> handler() {
        return null;
    }
}
