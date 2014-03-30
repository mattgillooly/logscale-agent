package com.logscale.agent.engine;

import org.junit.Test;

import static com.logscale.agent.testing.Assert.assertThrows;

public class EngineTest {
    @Test
    public void constructorThrowsOnNullConfig() {
        assertThrows(NullPointerException.class, () -> new Engine(null, null));
    }
}
