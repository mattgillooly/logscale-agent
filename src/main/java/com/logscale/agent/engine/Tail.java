package com.logscale.agent.engine;

import com.logscale.agent.event.*;
import com.logscale.agent.util.PushStream;
import com.logscale.logger.Logger;
import org.apache.commons.io.input.*;

import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public class Tail implements Runnable {
    private static final Logger log = Logger.getLogger();

    final Engine engine;
    final String path;

    public Tail(Engine engine, String path) {
        this.engine = engine;
        this.path = path;
    }

    @Override
    public void run() {
        eventStream().forEach(event -> {
            log.debug("event: %s", event);
            try {
                engine.session.stream(event);
            } catch (IOException e) {
                eventStream().close();
                throw new UncheckedIOException("trouble sending event: " + event, e);
            }
        });
    }

    public Stream<Event> eventStream() {
        TailStream tailStream = new TailStream();
        ExecutorService executorService = Executors.newSingleThreadExecutor(engine.threadFactory);
        executorService.submit(new Tailer(new File(path), tailStream, 500, false, false, 4096)::run);
        EventSource eventSource = new EventSource(String.format("tail[%s]", path), tailStream);
        return new EventBuffer(eventSource) {
            @Override
            public void close() {
                super.close();
                executorService.shutdownNow();
                tailStream.close();
                try {
                    executorService.awaitTermination(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    log.warn("interrupted waiting for tailer thread to end", e);
                }
            }
        };
    }

    private final class TailStream extends PushStream<String> implements Stream<String>, TailerListener {
        private final AtomicReference<Tailer> tailerRef = new AtomicReference<>();

        public TailStream() {
            super(1000);
        }

        @Override
        public void close() {
            log.info("closing tail stream for %s", path);
            tailerRef.getAndSet(null).stop();
        }

        @Override
        public void init(Tailer tailer) {
            log.info("initializing tail stream for %s", path);
            if (!tailerRef.compareAndSet(null, tailer)) {
                throw new IllegalStateException("init() called while tailer still open");
            }
        }

        @Override
        public void fileNotFound() {
            throw new RuntimeException("file not found: " + path);
        }

        @Override
        public void fileRotated() {
            log.info("file rotation detected");
        }

        @Override
        public void handle(String line) {
            if (line.trim().isEmpty()) {
                log.debug("discarding empty line from %s", path);
            } else {
                log.info("handling line from %s: %s", path, line);
                accept(line);
            }
        }

        @Override
        public void handle(Exception ex) {
            throw new RuntimeException("unhandled exception", ex);
        }
    }
}
