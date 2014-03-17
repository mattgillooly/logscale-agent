package com.logscale.agent.engine;

import com.logscale.logger.Logger;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class EngineTest {
    @Test
    public void testNoArgFactory() {
        Logger logger = Logger.getLogger();
        assertNotNull(logger);
        logger.info("blah");
    }
}
