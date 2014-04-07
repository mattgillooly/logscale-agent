package com.logscale.agent.engine;

import com.logscale.agent.event.Event;

import java.util.function.Consumer;
import java.util.stream.Stream;

public interface Processor {
    void init(Engine engine);
    Stream<Event> events();
    Consumer<Event> handler();
}
