package com.logscale.agent.engine;

import com.logscale.agent.event.Event;

import java.util.stream.Stream;

public interface SinkProcessor extends Processor {
    @Override
    default Stream<Event> events() {
        return null;
    }
}
