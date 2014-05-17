package com.logscale.agent.engine.proc;

import com.logscale.agent.engine.Engine;
import com.logscale.agent.event.*;
import com.logscale.agent.util.PushStream;
import com.logscale.logger.Logger;
import com.rabbitmq.client.*;
import org.apache.commons.codec.Charsets;

import java.io.*;
import java.net.*;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Stream;

public class RabbitMqProcessor implements SourceProcessor {
    private static final Logger log = Logger.getLogger();

    private final URI url;
    private final Set<String> queues;

    private Engine engine;
    public RabbitMqProcessor(String url, Set<String> queues) {
        try {
            this.url = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("bad url: " + url, e);
        }
        this.queues = queues;
    }

    @Override
    public void init(Engine engine) {
        this.engine = engine;
    }

    @Override
    public Stream<Event> events() {
        ConnectionFactory factory = new ConnectionFactory();
        try {
            factory.setUri(url);
        } catch (Exception e) {
            throw new RuntimeException("unexpected trouble setting url: " + url, e);
        }
        Connection conn;
        try {
            conn = factory.newConnection();
        } catch (IOException e) {
            throw new UncheckedIOException("could not get new connection", e);
        }
        ExecutorService executorService = Executors.newSingleThreadExecutor(engine.threadFactory);
        RabbitMqStream rabbitMqStream = new RabbitMqStream(conn, executorService, queues);
        executorService.submit(rabbitMqStream);
        EventSource eventSource = new EventSource(String.format("rabbitmq[%s]", url), rabbitMqStream);
        return new EventBuffer(eventSource) {
            @Override
            public void close() {
                super.close();
                executorService.shutdownNow();
                rabbitMqStream.close();
            }
        };
    }

    private static final class RabbitMqStream extends PushStream<String> implements Runnable {
        private final Connection conn;
        private final ExecutorService executorService;
        private final Set<String> queues;

        public RabbitMqStream(Connection conn, ExecutorService executorService, Set<String> queues) {
            super(1000);
            this.conn = conn;
            this.executorService = executorService;
            this.queues = queues;
        }

        @Override
        public void run() {
            Channel chan;
            try {
                chan = conn.createChannel();
            } catch (IOException e) {
                throw new UncheckedIOException("trouble creating rabbitmq channel", e);
            }
            try {
                queues.forEach((queue) -> executorService.submit(new Consumer(chan, queue)));
            } finally {
                try {
                    chan.close();
                } catch (IOException e) {
                    log.warn("trouble closing rabbitmq channel", e);
                }
            }
        }

        @Override
        public void close() {
            log.info("closing rabbitmq stream");
            try {
                conn.close();
            } catch (IOException e) {
                log.warn("trouble closing rabbitmq connection", e);
            }
        }

        private final class Consumer extends DefaultConsumer implements Runnable {
            private final String queue;

            public Consumer(Channel channel, String queue) {
                super(channel);
                this.queue = queue;
            }

            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                long deliveryTag = envelope.getDeliveryTag();
                String message = new String(body, Charsets.UTF_8);
                log.trace("received message: %s", message);
                accept(message);
                getChannel().basicAck(deliveryTag, false);
            }

            @Override
            public void run() {
                try {
                    getChannel().basicConsume(queue, false, "logscale-agent-rabbitmq-processor", this);
                } catch (IOException e) {
                    throw new UncheckedIOException("trouble consuming from queue: " + queue, e);
                }
            }
        }
    }
}
