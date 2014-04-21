package com.logscale.agent.engine;

import com.logscale.agent.event.Event;
import com.logscale.logger.Logger;

import javax.script.*;
import java.io.*;
import java.net.URLEncoder;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class ScriptProcessor implements Processor {
    private static final Logger log = Logger.getLogger();

    private final String url;

    private Processor scriptProcessor;

    public ScriptProcessor(String url) {
        this.url = url;
    }

    @Override
    public void init(Engine engine) {
        try {
            ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
            ScriptEngine scriptEngine = scriptEngineManager.getEngineByName("nashorn");
            String code =
                    "load('" + loadPath("classpath:com/logscale/agent/processor/context.js") + "');\n" +
                    "load('" + loadPath(url) + "');\n";
            log.debug("evaluating script:\n%s", code);
            scriptProcessor = (Processor) scriptEngine.eval(code);
        } catch (ScriptException se) {
            throw new RuntimeException("trouble evaluating script at url: " + url, se);
        }
    }

    @Override
    public Stream<Event> events() {
        try {
            return scriptProcessor.events();
        } catch (NoSuchMethodError ignore) {
            return null;
        }
    }

    @Override
    public Consumer<Event> handler() {
        try {
            return scriptProcessor.handler();
        } catch (NoSuchMethodError ignore) {
            return null;
        }
    }

    private static String loadPath(String url) {
        String loadPath = url;
        if (url.startsWith("classpath:")) {
            String resourceName = url.substring("classpath:".length());
            File trySrc = new File("src/main/resources/" + resourceName);
            if (trySrc.isFile() && trySrc.canRead()) {
                loadPath = trySrc.getPath();
            } else {
                trySrc = new File("ext/resources/" + resourceName);
                if (trySrc.isFile() && trySrc.canRead()) {
                    loadPath = trySrc.getPath();
                }
            }
        }
        try {
            return URLEncoder.encode(loadPath, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("unexpected encoding exception", e);
        }
    }
}
