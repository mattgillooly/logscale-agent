package com.logscale.agent.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.util.CharsetUtil;
import org.apache.commons.codec.binary.Hex;

import java.security.*;

public class Event {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public final String id;
    public final String source;
    public final long timestamp;
    public final long sequence;
    public final String content;

    public Event(String source, long timestamp, long sequence, String content) {
        this.source = source;
        this.timestamp = timestamp;
        this.sequence = sequence;
        this.content = content;
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("unexpected: no SHA-1", e);
        }
        digest.update(content.getBytes(CharsetUtil.UTF_8));
        id = Hex.encodeHexString(digest.digest());
    }

    public boolean matches(String query) {
        return content.contains(query);
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
