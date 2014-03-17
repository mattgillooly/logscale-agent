package com.logscale.agent.util;

import javax.net.ssl.*;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.*;

public class Ssl {
    private static String[] ROOT_RESOURCE_PATHS = {
            "com/logscale/agent/certs/sub.class1.server.ca.pem",
            "com/logscale/agent/certs/ca.pem"
    };

    public static SSLEngine createSslEngine() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagers(), null);
        SSLEngine engine = sslContext.createSSLEngine();
        engine.setUseClientMode(true);
        return engine;
    }

    private static TrustManager[] trustManagers() throws Exception {
        if (ROOT_RESOURCE_PATHS == null || ROOT_RESOURCE_PATHS.length == 0) {
            return null;
        }

        List<X509Certificate> certs = new ArrayList<>();
        for (String root : ROOT_RESOURCE_PATHS) {
            try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(root)) {
                certs.add(new sun.security.x509.X509CertImpl(is));
            }
        }

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init((KeyStore) null);
        TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
        for (TrustManager tm : trustManagers) {
            if (tm instanceof X509TrustManager) {
                Field f = tm.getClass().getDeclaredField("trustedCerts");
                f.setAccessible(true);
                @SuppressWarnings("unchecked")
                Collection<X509Certificate> trustedCerts = (Collection<X509Certificate>) f.get(tm);
                trustedCerts.addAll(certs);
            }
        }
        return trustManagers;
    }
}
