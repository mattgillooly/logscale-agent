package com.logscale.agent.engine.proc;

import com.logscale.agent.engine.Engine;
import com.logscale.agent.event.*;
import com.logscale.agent.util.PushStream;
import com.logscale.logger.Logger;
import redis.clients.jedis.*;

import java.net.*;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Stream;

public class RedisProcessor implements SourceProcessor {
    private static final Logger log = Logger.getLogger();

    private final URI url;
    private final Set<String> channels;

    private Engine engine;

    public RedisProcessor(String url, Set<String> channels) {
        try {
            this.url = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("bad url: " + url, e);
        }
        this.channels = channels;
    }

    @Override
    public void init(Engine engine) {
        this.engine = engine;
    }

    @Override
    public Stream<Event> events() {
        Jedis jedis = new Jedis(url);
        RedisStream redisStream = new RedisStream(jedis, channels);
        ExecutorService executorService = Executors.newSingleThreadExecutor(engine.threadFactory);
        executorService.submit(redisStream);
        EventSource eventSource = new EventSource(String.format("redis[%s]", url), redisStream);
        return new EventBuffer(eventSource) {
            @Override
            public void close() {
                super.close();
                executorService.shutdownNow();
                redisStream.close();
            }
        };
    }

    private static final class RedisStream extends PushStream<String> implements Runnable {
        private final Jedis jedis;
        private final Set<String> channels;
        private final Subscriber sub;

        public RedisStream(Jedis jedis, Set<String> channels) {
            super(1000);
            this.jedis = jedis;
            this.channels = channels;
            sub = new Subscriber();
        }

        @Override
        public void run() {
            jedis.subscribe(sub, channels.toArray(new String[channels.size()]));
        }

        @Override
        public void close() {
            log.info("closing redis stream");
            jedis.close();
        }

        private final class Subscriber extends JedisPubSub {
            @Override
            public void onMessage(String channel, String message) {
                log.debug("handling message from channel %s: %s", channel, message);
                accept(message);
            }

            @Override
            public void onPMessage(String pattern, String channel, String message) {
                log.debug("handling pattern (%s) message from channel %s: %s", pattern, channel, message);
                accept(message);
            }

            @Override
            public void onSubscribe(String channel, int subscribedChannels) {
                log.warn("got subscribe: %s, %s", channel, subscribedChannels);
            }

            @Override
            public void onUnsubscribe(String channel, int subscribedChannels) {
                log.warn("got unsubscribe: %s, %s", channel, subscribedChannels);
            }

            @Override
            public void onPUnsubscribe(String pattern, int subscribedChannels) {
                log.warn("got punsubscribe: %s, %s", pattern, subscribedChannels);
            }

            @Override
            public void onPSubscribe(String pattern, int subscribedChannels) {
                log.warn("got psubscribe: %s, %s", pattern, subscribedChannels);
            }
        }
    }
}
