package com.logscale.agent.engine;

import com.amazonaws.auth.*;
import com.amazonaws.services.kinesis.AmazonKinesisClient;
import com.amazonaws.services.kinesis.model.*;
import com.logscale.agent.event.Event;
import com.logscale.logger.Logger;
import org.apache.commons.codec.Charsets;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.stream.Stream;

public class KinesisEventBus extends EventBus {
    private static final Logger log = Logger.getLogger();

    private static final int KINESIS_RESULTS_LIMIT = 100;

    private final AWSCredentials creds;

    private final AmazonKinesisClient client;

    private final String streamName;

    public KinesisEventBus(String accessKey, String secretKey, String streamName) {
        creds = new BasicAWSCredentials(accessKey, secretKey);
        client = new AmazonKinesisClient(creds);
        this.streamName = streamName;
    }

    @Override
    public void start(Engine engine) {
        log.info("starting kinesis event bus from stream: %s", streamName);
        client.describeStream(streamName).getStreamDescription().getShards().forEach(shard -> {
            String shardIterator = client.getShardIterator(streamName, shard.getShardId(), "LATEST").getShardIterator();
            engine.threadFactory.newThread(new ShardPoller(shard, shardIterator)).start();
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

    private final class ShardPoller implements Runnable {
        private final Shard shard;
        private final String initialShardIterator;

        private ShardPoller(Shard shard, String initialShardIterator) {
            this.shard = shard;
            this.initialShardIterator = initialShardIterator;
        }

        @Override
        public void run() {
            log.info("started polling for stream: %s, shard: %s", streamName, shard.getShardId());
            String nextShardIterator = initialShardIterator;
            while (!isStopped()) {
                nextShardIterator = processShard(nextShardIterator);
            }
            log.info("stopped polling for stream: %s, shard: %s", streamName, shard.getShardId());
        }

        private String processShard(final String shardIterator) {
            GetRecordsResult result = result(shardIterator);
            eventIds(result).forEach(KinesisEventBus.this::handleEventId);
            return result.getNextShardIterator();
        }

        private GetRecordsResult result(String shardIterator) {
            GetRecordsRequest req = new GetRecordsRequest();
            req.setShardIterator(shardIterator);
            req.setLimit(KINESIS_RESULTS_LIMIT);
            req.setRequestCredentials(creds);
            return client.getRecords(req);
        }

        private Stream<String> eventIds(GetRecordsResult result) {
            List<Record> records = result.getRecords();
            if (records.isEmpty()) {
                return Stream.empty();
            }
            log.debug("got %s records", records.size());
            return records.stream().map(record -> {
                String eventId = new String(record.getData().array(), Charsets.UTF_8);
                log.debug("got event id: %s", eventId);
                return eventId;
            });
        }
    }
}
