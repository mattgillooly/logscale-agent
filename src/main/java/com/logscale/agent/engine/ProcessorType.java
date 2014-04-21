package com.logscale.agent.engine;

import com.fasterxml.jackson.databind.JsonNode;

public enum ProcessorType {
    file {
        @Override
        Processor create(JsonNode config) {
            return new FileProcessor(config.get("path").asText());
        }
    },
    index {
        @Override
        Processor create(JsonNode config) {
            return new IndexProcessor(config.get("period").asLong(), config.get("delay").asLong());
        }
    },
    script {
        @Override
        Processor create(JsonNode config) {
            return new ScriptProcessor(config.get("url").asText());
        }
    };

    abstract Processor create(JsonNode config);
}
