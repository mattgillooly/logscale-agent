package com.logscale.agent.event;

import com.logscale.agent.util.DelegatingStream;
import com.logscale.logger.Logger;

import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class EventSource extends DelegatingStream<Event> {
    private static final Logger log = Logger.getLogger();

    private final String name;
    private final Stream<String> messageStream;
    private final AtomicLong sequence;

    public EventSource(String name, Stream<String> messageStream) {
        this(name, messageStream, new AtomicLong());
    }

    private EventSource(String name, Stream<String> messageStream, AtomicLong sequence) {
        super(messageStream.map(msg -> event(name, sequence.incrementAndGet(), msg)));
        this.name = name;
        this.messageStream = messageStream;
        this.sequence = sequence;
    }

    public String getName() {
        return name;
    }

    public long numEvents() {
        return sequence.get();
    }

    @Override
    public void close() {
        messageStream.close();
    }

    private static Event event(String name, long sequence, String message) {
        // TODO: use real date parser
        long timestamp = System.currentTimeMillis();
        Event evt = new Event(name, timestamp, sequence, message);
        log.trace("created new event: %s", evt);
        return evt;
    }
}
