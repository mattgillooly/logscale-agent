package com.logscale.agent.engine;

import com.logscale.agent.event.Event;
import com.logscale.logger.Logger;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.*;
import java.util.function.Consumer;

@RunWith(JUnit4.class)
public class IndexProcessorTest {
    private static final Logger log = Logger.getLogger();

    protected Processor processor;
    private long testDelta;

    private final String eventSource = getClass().getName();

    @Before
    public void before() {
        processor = new IndexProcessor(10000, 0);
        log.info("testing with processor: %s", processor);
        testDelta = -System.currentTimeMillis();
    }

    @After
    public void after() {
        testDelta += System.currentTimeMillis();
        log.info("test complete: %s millis", testDelta);
    }

    @Test
    public void processSimple() {
        processor.handler().accept(new Event(eventSource, 0, 1, "hello world"));
    }

    @Test
    public void processe1k() {
        Consumer<Event> handler = processor.handler();
        for (int i = 0; i < 1000; i++) {
            handler.accept(new Event(eventSource, 0, i, "value " + i));
        }
    }

    @Test
    public void process1m() {
        Consumer<Event> handler = processor.handler();
        for (int i = 0; i < 1000000; i++) {
            handler.accept(new Event(eventSource, 0, i, "value " + i));
        }
    }

    public void processVarLogSystem() throws Exception {
        Consumer<Event> handler = processor.handler();
        try(BufferedReader br = new BufferedReader(new FileReader("/var/log/system.log"))) {
            long seq = 0;
            String line;
            while ((line = br.readLine()) != null) {
                handler.accept(new Event(eventSource, System.currentTimeMillis(), seq++, line));
            }
            log.info("processed %s lines from /var/log/system.log", seq);
        }
    }
}
