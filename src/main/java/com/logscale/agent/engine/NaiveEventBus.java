package com.logscale.agent.engine;

import com.logscale.agent.event.Event;

public class NaiveEventBus extends EventBus {
    @Override
    public void push(Event event) {
        handleEvent(event);
    }
}
