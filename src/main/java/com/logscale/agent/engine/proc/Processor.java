package com.logscale.agent.engine.proc;

import com.logscale.agent.engine.Engine;
import com.logscale.agent.event.Event;

import java.util.function.Consumer;
import java.util.stream.Stream;

public interface Processor {
    void init(Engine engine);
    Stream<Event> events();
    Consumer<Event> handler();
}
