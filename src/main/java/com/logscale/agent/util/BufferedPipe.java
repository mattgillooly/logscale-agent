package com.logscale.agent.util;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.*;

public class BufferedPipe<T> implements Consumer<T>, Supplier<T> {
    private final ArrayBlockingQueue<T> queue;

    public BufferedPipe(int capacity) {
        this(new ArrayBlockingQueue<T>(capacity));
    }

    private BufferedPipe(ArrayBlockingQueue<T> queue) {
        this.queue = queue;
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public void accept(T t) {
        try {
            queue.put(t);
        } catch (InterruptedException e) {
            throw new RuntimeException("interrupted during put()", e);
        }
    }

    @Override
    public T get() {
        try {
            return queue.take();
        } catch (InterruptedException e) {
            throw new RuntimeException("interrupted during take()", e);
        }
    }
}
