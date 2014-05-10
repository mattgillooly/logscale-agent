package com.logscale.agent.engine;

import com.amazonaws.auth.*;
import com.amazonaws.services.kinesis.AmazonKinesisClient;
import com.amazonaws.services.kinesis.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logscale.agent.event.Event;
import com.logscale.logger.Logger;
import org.apache.commons.codec.Charsets;
import org.bouncycastle.util.encoders.Base64;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public class KinesisEventBus extends EventBus {
    private static final Logger log = Logger.getLogger();

    private static final int KINESIS_RESULTS_LIMIT = 100;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final AWSCredentials creds;

    private final AmazonKinesisClient client;

    private final String streamName;

    private AtomicReference<Engine> engineRef = new AtomicReference<>();

    public KinesisEventBus(String accessKey, String secretKey, String streamName) {
        creds = new BasicAWSCredentials(accessKey, secretKey);
        client = new AmazonKinesisClient(creds);
        this.streamName = streamName;
    }

    @Override
    public void start(Engine engine) {
        super.start(engine);
        client.describeStream(streamName).getStreamDescription().getShards().forEach(shard -> {
            String shardIterator = client.getShardIterator(streamName, shard.getShardId(), "LATEST").getShardIterator();
            engine.threadFactory.newThread(new ShardPoller(shard, shardIterator)).start();
        });
        engineRef.set(engine);
    }

    @Override
    public void stop() {
        engineRef.set(null);
        super.stop();
    }

    @Override
    public void push(Event event) {
        ByteBuffer data = ByteBuffer.wrap(event.toString().getBytes());
        String partitionKey = event.id;
        client.putRecord(streamName, data, partitionKey);
    }

    private boolean isStopped() {
        return engineRef.get() == null;
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
            events(result).forEach(KinesisEventBus.this::handleEvent);
            return result.getNextShardIterator();
        }

        private GetRecordsResult result(String shardIterator) {
            GetRecordsRequest req = new GetRecordsRequest();
            req.setShardIterator(shardIterator);
            req.setLimit(KINESIS_RESULTS_LIMIT);
            req.setRequestCredentials(creds);
            return client.getRecords(req);
        }

        private Stream<Event> events(GetRecordsResult result) {
            List<Record> records = result.getRecords();
            if (records.isEmpty()) {
                return Stream.empty();
            }
            log.debug("got %s records", records.size());
            return records.stream().map(record -> {
                log.trace("getting event for result: %s", result);
                byte[] bytes = record.getData().array();
                Event event;
                try {
                    event = objectMapper.readValue(bytes, Event.class);
                } catch (IOException e) {
                    UncheckedIOException resultError = resultError(e, record);
                    log.error("bad record", resultError);
                    throw resultError;
                }
                return event;
            });
        }

        private UncheckedIOException resultError(IOException ioe, Record record) {
            return new UncheckedIOException("trouble reading event from record: " +
                    "stream = " + streamName +
                    ", shard = " + shard.getShardId() +
                    ", partition = " + record.getPartitionKey() +
                    ", sequence = " + record.getSequenceNumber() + ": " +
                    new String(Base64.encode(record.getData().array()), Charsets.UTF_8),
                ioe);
        }
    }
}
