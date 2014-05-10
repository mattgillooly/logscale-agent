package com.logscale.agent.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logscale.agent.util.Sha1;

public class Event {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public final String id;
    public final String source;
    public final long timestamp;
    public final long sequence;
    public final CharSequence content;
    public final CharSequence[] partitions;

    public Event(@JsonProperty("source") String source,
                 @JsonProperty("timestamp") long timestamp,
                 @JsonProperty("sequence") long sequence,
                 @JsonProperty("content") CharSequence content,
                 @JsonProperty("partitions") CharSequence ... partitions) {
        this.source = source;
        this.timestamp = timestamp;
        this.sequence = sequence;
        this.content = content;
        id = Sha1.hexDigest(content);
        if (partitions == null || partitions.length == 0) {
            this.partitions = null;
        } else {
            this.partitions = partitions;
        }
    }

    @Override
    public String toString() {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("unable to serialize event to JSON", e);
        }
    }
}
