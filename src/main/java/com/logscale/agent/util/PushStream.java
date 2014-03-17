package com.logscale.agent.util;

import java.util.function.Consumer;
import java.util.stream.Stream;

public class PushStream<T> extends DelegatingStream<T> implements Consumer<T> {
    protected final BufferedPipe<T> pipe;

    public PushStream(int capacity) {
        this(new BufferedPipe<T>(capacity));
    }

    public boolean isEmpty() {
        return pipe.isEmpty();
    }

    private PushStream(BufferedPipe<T> pipe) {
        super(Stream.generate(pipe::get));
        this.pipe = pipe;
    }

    @Override
    public void accept(T t) {
        pipe.accept(t);
    }
}
