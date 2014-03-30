package com.logscale.agent.event;

import com.logscale.agent.util.PullStream;
import com.logscale.logger.Logger;

public class EventBuffer extends PullStream<Event> {
    private static final Logger log = Logger.getLogger();

    public static final int DEFAULT_QUEUE_CAPACITY = 1000;

    private final EventSource source;

    public EventBuffer(EventSource source) {
        this(source, DEFAULT_QUEUE_CAPACITY);
    }

    public EventBuffer(EventSource source, int capacity) {
        super(source, capacity);
        this.source = source;
    }

    @Override
    public void close() {
        super.close();
        source.close();
        log.info("closing event source %s after %s events", source.getName(), source.numEvents());
    }
}
