package com.logscale.agent;

import org.junit.*;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;

public class MainTest {
    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Test
    public void testUsage() {
        exit.expectSystemExitWithStatus(Main.EXIT_CODE_USAGE);
        Main.main();
    }

    @Test
    public void testBadUrl() {
        exit.expectSystemExitWithStatus(Main.EXIT_CODE_BAD_URL);
        Main.main("not a url");
    }
}
