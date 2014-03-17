package com.logscale.agent;

import com.logscale.logger.Logger;
import com.sun.akuma.CLibrary;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import sun.misc.Signal;

import java.io.*;
import java.net.*;
import java.security.Security;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    private static final String MAIN_PROPERTIES_RESOURCE = "com/logscale/agent/main.properties";

    public static final int EXIT_CODE_USAGE = 2;
    public static final int EXIT_CODE_BAD_URL = 3;
    public static final int EXIT_CODE_UNEXPECTED_FAIL = 4;

    private static final int[] FETCH_RETRY_DELAYS_SEC = {1, 2, 5, 10, 20, 30};

    private final Logger log = Logger.getLogger();

    private final URL url;

    private final AtomicBoolean isShutdown = new AtomicBoolean(false);
    private final AtomicBoolean doReload = new AtomicBoolean(false);
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    public Main(URL url) {
        if (url == null) throw new IllegalArgumentException("null url");
        this.url = url;
    }

    private int loop() {
        log.banner(Level.INFO, () -> "starting loop: " + url.toString());

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "shutdown-hook"));
        Signal.handle(new Signal("HUP"), this::signal);

        int fetchAttempt = 0;
        while (!isShutdown.get()) {
            if (doReload.get()) {
                log.debug("got reload flag: resetting...");
                fetchAttempt = 0;
            }

            doReload.set(false);
            Agent agent;
            try {
                agent = fetch();
            } catch (Exception e) {
                int delaySec = FETCH_RETRY_DELAYS_SEC[Integer.min(fetchAttempt++, FETCH_RETRY_DELAYS_SEC.length - 1)];
                log.error("trouble with fetch attempt #" + fetchAttempt + ", sleeping " + delaySec + "s before retry" + e.getMessage());
                log.trace(e);
                try {
                    for (int i = (int) (TimeUnit.SECONDS.toMillis(delaySec) / 200); i > 0 && !doReload.get(); i--) {
                        Thread.sleep(200);
                    }
                } catch (InterruptedException ie) {
                    log.warn("interrupted, shutting down", ie);
                    isShutdown.set(true);
                }
                continue;
            }
            fetchAttempt = 0;
            log.info("starting agent:\n%s", agent);
            agent.start();
            long nextHeartbeat = System.currentTimeMillis() + 30000;
            while (!doReload.get() && !isShutdown.get()) {
                if (System.currentTimeMillis() >= nextHeartbeat) {
                    log.debug("heartbeat");
                    nextHeartbeat = System.currentTimeMillis() + 30000;
                }

                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    log.warn("interrupted, shutting down", e);
                    isShutdown.set(true);
                }
            }
            agent.stop();
        }

        consoleLog("loop exiting");
        shutdownLatch.countDown();
        return 0;
    }

    public void shutdown() {
        log.warn("shutdown hook called");
        ThreadContext.push("shutdown");
        try {
            isShutdown.set(true);

            consoleLog("waiting for shutdown latch");
            try {
                if (shutdownLatch.await(20, TimeUnit.SECONDS)) {
                    consoleLog("got to shutdown latch");
                } else {
                    consoleLog("timed out waiting for shutdown latch");
                }
            } catch (InterruptedException e) {
                consoleLog("WARN: got interrupted waiting for shutdown latch");
            }

            File pidfile = new File(System.getenv().getOrDefault("PIDFILE", "log/logscale-agent.pid"));
            if (!pidfile.exists()) {
                consoleLog("WARN: missing PIDFILE: " + pidfile);
            } else if (pidfile.delete()) {
                consoleLog("deleted PIDFILE: " + pidfile);
            } else {
                consoleLog("WARN: unable to delete PIDFILE: " + pidfile);
            }
        } finally {
            ThreadContext.pop();
        }

        log.warn("shutdown hook completed");
    }

    public void signal(Signal signal) {
        log.info("received signal %s", signal);
        if ("HUP".equals(signal.getName())) {
            doReload.set(true);
            log.info("set reload flag");
        } else {
            log.warn("ignoring unexpected SIG%s", signal.getName());
        }
    }

    private Agent fetch() {
        log.info("fetching: %s", url);
        try (InputStream in = url.openStream()) {
            Yaml yml = new Yaml(new Constructor(Agent.class));
            Object v = yml.load(in);
            if (v == null) {
                throw new RuntimeException("unexpected null agent config at URL " + url);
            }
            if (!v.getClass().isAssignableFrom(Agent.class)) {
                throw new RuntimeException("unexpected class (" + v.getClass() + ") from agent config at URL " + url + ": " + v);
            }
            return (Agent) v;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static {
        try {
            try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(MAIN_PROPERTIES_RESOURCE)) {
                System.getProperties().load(is);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not load main properties resource: " + MAIN_PROPERTIES_RESOURCE, e);
        }

        ThreadContext.put("pid", String.valueOf(CLibrary.LIBC.getpid()));

        Security.addProvider(new BouncyCastleProvider());
    }

    private static void writePidfile() throws Exception {
        File pidfile = new File(System.getenv().getOrDefault("PIDFILE", "log/logscale-agent.pid"));
        if (pidfile.exists()) {
            consoleLog("WARN: existing PIDFILE: " + pidfile);
        }

        String pid = String.valueOf(CLibrary.LIBC.getpid());
        FileUtils.write(pidfile, pid);
        consoleLog("wrote pid (" + pid + ") to PIDFILE (" + pidfile + ")");
    }

    public static void main(String ... args) {
        int status;
        try {

            if (args.length != 1) {
                System.err.println("Usage: logscale-agent <config-url>");
                System.err.println();
                System.err.println("   or  logscale-agent --version");
                System.err.println();
                status = EXIT_CODE_USAGE;
            } else if ("--version".equals(args[0])) {
                System.out.println("logscale-agent version " + System.getProperty("logscale.agent.version"));
                status = 0;
            } else {
                URL url = new URL(args[0]);
                writePidfile();
                status = new Main(url).loop();
            }
        } catch (MalformedURLException e) {
            System.err.println("illegal config-url: " + e.getMessage());
            status = EXIT_CODE_BAD_URL;
        } catch (Throwable t) {
            consoleLog("FATAL: unexpected failure");
            t.printStackTrace();
            try { Thread.sleep(2000); } catch (InterruptedException ignore) { }
            status = EXIT_CODE_UNEXPECTED_FAIL;
        }
        System.exit(status);
    }

    private static synchronized void consoleLog(String message) {
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS").format(new Date());
        System.err.printf("%s %s\n", date, message);
    }
}
