package com.logscale.agent.engine;

import com.fasterxml.jackson.databind.JsonNode;

public enum EventBusType {
    naive {
        @Override
        EventBus create(JsonNode config) {
            return new NaiveEventBus();
        }
    },

    kinesis {
        @Override
        EventBus create(JsonNode config) {
            return new KinesisEventBus(config.get("accessKey").asText(), config.get("secretKey").asText(), config.get("stream").asText());
        }
    };

    abstract EventBus create(JsonNode config);
}
