package com.logscale.agent.ws.msg;

import com.fasterxml.jackson.databind.JsonNode;
import com.logscale.agent.Agent;
import com.logscale.mixins.JsonMessage;
import io.netty.util.CharsetUtil;
import org.bouncycastle.openssl.PEMReader;

import java.io.*;
import java.net.URLEncoder;
import java.security.*;
import java.util.Base64;

public class AuthenticateMessage implements JsonMessage {
    public final String type = "authentication";
    public final String agent;
    public final String agentVersion;
    public final long timestamp;
    public final String session;
    public final String signature;

    public AuthenticateMessage(Agent agent, JsonNode startSessionMessage) throws Exception {
        this.agent = agent.name;
        agentVersion = System.getProperty("logscale.agent.version", "unknown");
        timestamp = System.currentTimeMillis();
        session = startSessionMessage.get("session").asText();

        StringBuilder envelopeBuilder = new StringBuilder();
        envelopeBuilder.append("agent=").append(urlEncode(this.agent));
        envelopeBuilder.append("&agentVersion=").append(urlEncode(agentVersion));
        envelopeBuilder.append("&session=").append(urlEncode(session));
        envelopeBuilder.append("&timestamp=").append(timestamp);
        envelopeBuilder.append("&type=").append(urlEncode(type));

        KeyPair keyPair = (KeyPair) new PEMReader(new StringReader(agent.key)).readObject();
        Signature sha1 = Signature.getInstance("SHA1withRSA");
        sha1.initSign(keyPair.getPrivate());
        sha1.update(envelopeBuilder.toString().getBytes(CharsetUtil.UTF_8));
        signature = Base64.getEncoder().encodeToString(sha1.sign());
    }

    private static String urlEncode(String v) {
        try {
            return URLEncoder.encode(v, CharsetUtil.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("unexpected trouble getting utf-8", e);
        }
    }
}
