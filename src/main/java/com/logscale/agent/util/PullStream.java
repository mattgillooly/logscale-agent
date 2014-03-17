package com.logscale.agent.util;

import java.util.concurrent.*;
import java.util.stream.Stream;

public class PullStream<T> extends PushStream<T> implements Runnable {
    protected final Stream<T> supplier;
    private final ExecutorService executorService;

    public PullStream(Stream<T> supplier, int queueCapacity) {
        super(queueCapacity);
        this.supplier = supplier;
        executorService = Executors.newSingleThreadExecutor();
        executorService.submit(this);
    }

    @Override
    public void run() {
        supplier.forEach(this::accept);
    }

    public void close() {
        executorService.shutdownNow();
    }
}
