package com.logscale.agent.ws.msg;

import com.logscale.agent.event.Event;
import com.logscale.mixins.JsonMessage;

public class EventMessage implements JsonMessage {
    public final String type = "event";
    public final Event event;

    public EventMessage(Event event) {
        this.event = event;
    }
}
