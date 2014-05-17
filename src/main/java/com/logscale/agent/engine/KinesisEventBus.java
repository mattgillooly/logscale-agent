package com.logscale.agent.engine;

import com.amazonaws.auth.*;
import com.amazonaws.services.kinesis.AmazonKinesisAsyncClient;
import com.logscale.agent.event.Event;
import com.logscale.agent.util.KinesisShardPoller;
import com.logscale.logger.Logger;
import org.apache.commons.codec.Charsets;

import java.nio.ByteBuffer;

public class KinesisEventBus extends EventBus {
    private static final Logger log = Logger.getLogger();

    private final AWSCredentials creds;

    private final AmazonKinesisAsyncClient client;

    private final String streamName;

    public KinesisEventBus(String accessKey, String secretKey, String streamName) {
        creds = new BasicAWSCredentials(accessKey, secretKey);
        client = new AmazonKinesisAsyncClient(creds);
        this.streamName = streamName;
    }

    @Override
    public void start(Engine engine) {
        log.info("starting kinesis event bus from stream: %s", streamName);
        client.describeStream(streamName).getStreamDescription().getShards().forEach(shard -> {
            String shardIterator = client.getShardIterator(streamName, shard.getShardId(), "LATEST").getShardIterator();
            engine.threadFactory.newThread(new KinesisShardPoller(creds, client, shard, shardIterator, this::handleEventId)).start();
        });
        super.start(engine);
    }

    @Override
    public void stop() {
        super.stop();
    }

    @Override
    public void push(Event event) {
        super.push(event);
        log.debug("sending event to kinesis stream %s: %s", streamName, event.id);
        ByteBuffer data = ByteBuffer.wrap(event.id.getBytes(Charsets.UTF_8));
        String partitionKey = event.id;
        client.putRecord(streamName, data, partitionKey);
    }
}
