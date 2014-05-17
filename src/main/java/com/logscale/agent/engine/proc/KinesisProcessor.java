package com.logscale.agent.engine.proc;

import com.amazonaws.auth.*;
import com.amazonaws.services.kinesis.AmazonKinesisAsyncClient;
import com.logscale.agent.engine.Engine;
import com.logscale.agent.event.*;
import com.logscale.agent.util.*;
import com.logscale.logger.Logger;

import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Stream;

public class KinesisProcessor implements SourceProcessor {
    private static final Logger log = Logger.getLogger();

    private final AWSCredentials creds;
    private final Set<String> streamNames;

    private ExecutorService executorService;
    private AmazonKinesisAsyncClient client;

    public KinesisProcessor(String accessKey, String secretKey, Set<String> streamNames) {
        creds = new BasicAWSCredentials(accessKey, secretKey);
        this.streamNames = streamNames;
    }

    @Override
    public void init(Engine engine) {
        executorService = Executors.newSingleThreadExecutor(engine.threadFactory);
        client = new AmazonKinesisAsyncClient(creds, executorService);
    }

    @Override
    public Stream<Event> events() {
        PushStream<String> stream = new PushStream<>(1000);
        executorService.submit(() -> {
            streamNames.forEach((streamName) -> executorService.submit(() -> {
                log.info("starting kinesis event processor for stream: %s", streamName);
                client.describeStream(streamName).getStreamDescription().getShards().forEach(shard -> {
                    String shardIterator = client.getShardIterator(streamName, shard.getShardId(), "LATEST").getShardIterator();
                    executorService.submit(new KinesisShardPoller(creds, client, shard, shardIterator, stream::accept));
                });
            }));
        });
        EventSource eventSource = new EventSource("kinesis", stream);
        return new EventBuffer(eventSource) {
            @Override
            public void close() {
                super.close();
                executorService.shutdownNow();
                client.shutdown();
                stream.close();
            }
        };
    }
}
