package com.logscale.agent.engine.proc;

import com.google.common.collect.*;
import com.logscale.agent.engine.Engine;
import com.logscale.agent.event.Event;
import com.logscale.agent.util.*;
import com.logscale.logger.Logger;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class IndexProcessor implements Processor {
    private static final Logger log = Logger.getLogger();

    private static final Map<Long, String> PERIOD_SOURCE_NAMES = new HashMap<>();
    static {
        PERIOD_SOURCE_NAMES.put(10000l, "index[10s]");
    }

    private final long period;
    private final long delay;

    private final String sourceName;
    private final AtomicLong seq = new AtomicLong(0);

    private final PushStream<Event> events = new PushStream<>(1000);

    public IndexProcessor(long period, long delay) {
        sourceName = PERIOD_SOURCE_NAMES.get(period);
        if (sourceName == null) {
            throw new IllegalArgumentException("period (" + period + ") not one of allowed: " + Arrays.toString(PERIOD_SOURCE_NAMES.keySet().toArray()));
        }
        this.period = period;
        this.delay = delay;
    }

    private final ConcurrentNavigableMap<Long, Segment> segments = new ConcurrentSkipListMap<>();

    @Override
    public void init(Engine engine) {
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(engine.threadFactory);
        executorService.scheduleWithFixedDelay(() -> {
            long reapCreatedTs = System.currentTimeMillis() - delay;
            long reapTruncTs = reapCreatedTs - period;
            segments.headMap(reapTruncTs).forEach((ts, segment) -> {
                if (segment.createdAt < reapCreatedTs) {
                    segments.remove(ts);
                    log.info("reaped segment: %s", segment);
                    segment.write(events);
                }
            });
        }, delay, delay, TimeUnit.MILLISECONDS);
    }

    @Override
    public Stream<Event> events() {
        return events;
    }

    @Override
    public Consumer<Event> handler() {
        return (event) -> {
            if (event.source.startsWith("index[")) {
                return;
            }
            long truncTs = event.timestamp - (event.timestamp % period);
            Segment seg = segments.get(truncTs);
            if (seg == null) {
                seg = new Segment(truncTs);
                Segment previousSeg = segments.putIfAbsent(truncTs, seg);
                if (previousSeg != null) {
                    log.info("lost indexing race: adding new segment at %s", truncTs);
                    seg = previousSeg;
                }
            }
            seg.add(event);
        };
    }

    public final class Segment {
        private final long startTs;
        private final long createdAt;

        private final Multimap<CharSequence, String> tokensToEventIds;

        public Segment(long startTs) {
            this.startTs = startTs;
            createdAt = System.currentTimeMillis();
            tokensToEventIds = Multimaps.synchronizedSetMultimap(HashMultimap.create());
        }

        public void add(Event event) {
            new TokenIterator(event.content).forEachRemaining((token) -> {
                tokensToEventIds.get(token.toString().toLowerCase()).add(event.id);
            });
        }

        public void write(PushStream<Event> out) {
            tokensToEventIds.asMap().forEach((token, eventIds) -> {
                StringBuilder json = new StringBuilder(24 + token.length() + (eventIds.size() * 43));
                json.append("{\"token\":\"").append(token).append("\",");
                json.append("\"events\":[");
                Iterator<String> eventIdIter = eventIds.iterator();
                boolean needComma = false;
                while (eventIdIter.hasNext()) {
                    if (needComma) {
                        json.append(',');
                    } else {
                        needComma = true;
                    }
                    json.append("\"").append(eventIdIter.next()).append("\"");
                }
                json.append("]}");
                Event event = new Event(sourceName, startTs, seq.incrementAndGet(), json.toString(), sourceName, token.subSequence(0, 1), token);
                out.accept(event);
            });
        }
    }
}
