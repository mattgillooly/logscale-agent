package com.logscale.agent.engine.proc;

import com.amazonaws.auth.*;
import com.amazonaws.services.sqs.AmazonSQSAsyncClient;
import com.amazonaws.services.sqs.model.*;
import com.logscale.agent.engine.Engine;
import com.logscale.agent.event.*;
import com.logscale.agent.util.PushStream;
import com.logscale.logger.Logger;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public class SqsProcessor implements SourceProcessor {
    private static final Logger log = Logger.getLogger();

    private static final int MAX_MESSAGES = 100;

    private final AWSCredentials creds;
    private final Set<String> queueUrls;

    private Engine engine;

    public SqsProcessor(String accessKey, String secretKey, Set<String> queueUrls) {
        creds = new BasicAWSCredentials(accessKey, secretKey);
        this.queueUrls = queueUrls;
    }

    @Override
    public void init(Engine engine) {
        this.engine = engine;
    }

    @Override
    public Stream<Event> events() {
        ExecutorService executorService = Executors.newSingleThreadExecutor(engine.threadFactory);
        AmazonSQSAsyncClient client = new AmazonSQSAsyncClient(creds, executorService);
        SqsStream sqsStream = new SqsStream(client, executorService, queueUrls);
        executorService.submit(sqsStream);
        EventSource eventSource = new EventSource("sqs", sqsStream);
        return new EventBuffer(eventSource) {
            @Override
            public void close() {
                super.close();
                executorService.shutdownNow();
                sqsStream.close();
            }
        };
    }

    private static final class SqsStream extends PushStream<String> implements Runnable {
        private final AmazonSQSAsyncClient client;
        private final ExecutorService executorService;
        private final Set<String> queueUrls;
        private final AtomicBoolean isRunning = new AtomicBoolean();

        public SqsStream(AmazonSQSAsyncClient client, ExecutorService executorService, Set<String> queueUrls) {
            super(1000);
            this.client = client;
            this.executorService = executorService;
            this.queueUrls = queueUrls;
        }

        @Override
        public void run() {
            queueUrls.forEach((queueUrl) -> executorService.submit(() -> {
                while (isRunning.get()) {
                    ReceiveMessageRequest request = new ReceiveMessageRequest(queueUrl);
                    request.setMaxNumberOfMessages(MAX_MESSAGES);
                    ReceiveMessageResult result = client.receiveMessage(request);
                    result.getMessages().parallelStream().forEach((msg) -> {
                        String receiptHandle = msg.getReceiptHandle();
                        String body = msg.getBody();
                        log.debug("got message: %s", body);
                        accept(body);
                        client.deleteMessageAsync(new DeleteMessageRequest(queueUrl, receiptHandle));
                    });
                }
            }));
        }

        @Override
        public void close() {
            log.info("closing sqs stream");
            isRunning.set(false);
            client.shutdown();
        }
    }
}
