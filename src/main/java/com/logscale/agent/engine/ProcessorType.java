package com.logscale.agent.engine;

import com.fasterxml.jackson.databind.JsonNode;

public enum ProcessorType {
    file {
        @Override
        Processor create(JsonNode node) {
            return new FileProcessor(node.get("path").asText());
        }
    },
    script {
        @Override
        Processor create(JsonNode node) {
            return new ScriptProcessor(node.get("url").asText());
        }
    };

    abstract Processor create(JsonNode node);
}
