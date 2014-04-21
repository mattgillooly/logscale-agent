package com.logscale.agent.util;

import com.google.common.hash.Hashing;
import org.apache.commons.codec.binary.Hex;

public class Sha1 {
    public static String hexDigest(CharSequence content) {
        return Hex.encodeHexString(digest(content));
    }

    public static byte[] digest(CharSequence content) {
        return Hashing.sha1().hashUnencodedChars(content).asBytes();
    }
}
