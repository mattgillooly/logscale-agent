package com.logscale.agent.engine.proc;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;

public enum ProcessorType {
    file {
        @Override
        public Processor create(Config cfg) {
            return new FileProcessor(cfg.str("path"));
        }
    },
    index {
        @Override
        public Processor create(Config cfg) {
            return new IndexProcessor(cfg.lng("period"), cfg.lng("delay"));
        }
    },
    kinesis {
        @Override
        public Processor create(Config cfg) {
            return new KinesisProcessor(cfg.str("accessKey"), cfg.str("secretKey"), cfg.strset("streamNames"));
        }
    },
    rabbitmq {
        @Override
        public Processor create(Config cfg) {
            return new RabbitMqProcessor(cfg.str("url"), cfg.strset("queues"));
        }
    },
    redis {
        @Override
        public Processor create(Config cfg) {
            return new RedisProcessor(cfg.str("url"), cfg.strset("channels"));
        }
    },
    script {
        @Override
        public Processor create(Config cfg) {
            return new ScriptProcessor(cfg.str("url"));
        }
    },
    sqs {
        @Override
        public Processor create(Config cfg) {
            return new SqsProcessor(cfg.str("accessKey"), cfg.str("secretKey"), cfg.strset("queueUrls"));
        }
    },
    websocket {
        @Override
        public Processor create(Config cfg) {
            return new WebsocketProcessor(cfg.str("url"));
        }
    };

    protected abstract Processor create(Config cfg);

    public Processor create(JsonNode node) {
        return create(new Config(node));
    }

    private static class Config {
        final JsonNode node;

        Config(JsonNode node) {
            this.node = node;
        }

        String str(String fieldName) {
            return node.get(fieldName).asText();
        }

        long lng(String fieldName) {
            return node.get(fieldName).asLong();
        }

        private Set<String> strset(String fieldName) {
            Set<String> strs = new TreeSet<>();
            node.get(fieldName).elements().forEachRemaining((elem) -> strs.add(elem.asText()));
            return strs;
        }
    }
}
