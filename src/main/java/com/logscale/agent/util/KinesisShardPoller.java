package com.logscale.agent.util;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.kinesis.AmazonKinesisAsyncClient;
import com.amazonaws.services.kinesis.model.*;
import com.logscale.logger.Logger;
import org.apache.commons.codec.Charsets;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class KinesisShardPoller implements Runnable {
    private static final Logger log = Logger.getLogger();

    private static final int KINESIS_RESULTS_LIMIT = 100;

    private final AWSCredentials creds;
    private final AmazonKinesisAsyncClient client;
    private final Shard shard;
    private final String initialShardIterator;
    private final Consumer<String> consumer;

    public KinesisShardPoller(AWSCredentials creds, AmazonKinesisAsyncClient client, Shard shard, String initialShardIterator, Consumer<String> consumer) {
        this.creds = creds;
        this.client = client;
        this.shard = shard;
        this.initialShardIterator = initialShardIterator;
        this.consumer = consumer;
    }

    @Override
    public void run() {
        log.info("started polling for shard: %s", shard.getShardId());
        String nextShardIterator = initialShardIterator;
        while (!Thread.interrupted()) {
            nextShardIterator = processShard(nextShardIterator);
        }
        log.info("stopped polling for shard: %s", shard.getShardId());
    }

    private String processShard(final String shardIterator) {
        GetRecordsResult result = result(shardIterator);
        messages(result).forEach(consumer::accept);
        return result.getNextShardIterator();
    }

    private GetRecordsResult result(String shardIterator) {
        GetRecordsRequest req = new GetRecordsRequest();
        req.setShardIterator(shardIterator);
        req.setLimit(KINESIS_RESULTS_LIMIT);
        req.setRequestCredentials(creds);
        return client.getRecords(req);
    }

    private Stream<String> messages(GetRecordsResult result) {
        List<Record> records = result.getRecords();
        if (records.isEmpty()) {
            log.trace("no records from kinesis");
            return Stream.empty();
        }
        log.debug("got %s records", records.size());
        return records.stream().map(record -> {
            String message = new String(record.getData().array(), Charsets.UTF_8);
            log.debug("got message: %s", message);
            return message;
        });
    }
}
