package com.logscale.agent.util;

import org.apache.commons.codec.Charsets;
import org.apache.commons.codec.binary.Hex;

import java.security.*;

// TODO: look at sphlib instead of MessageDigest,
// TODO: consider operating on CharSequence directly
public class Sha1 {
    private static ThreadLocal<MessageDigest> LOCAL_SHA1 = new ThreadLocal<>();

    public static String hexDigest(String content) {
        return Hex.encodeHexString(digest(content));
    }

    public static byte[] digest(String content) {
        MessageDigest sha1 = LOCAL_SHA1.get();
        if (sha1 == null) {
            try {
                sha1 = MessageDigest.getInstance("SHA-1");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("unexpected: no SHA-1", e);
            }
            LOCAL_SHA1.set(sha1);
        }
        try {
            return sha1.digest(content.getBytes(Charsets.UTF_8));
        } finally {
            sha1.reset();
        }
    }
}
