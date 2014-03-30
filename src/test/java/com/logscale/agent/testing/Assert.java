package com.logscale.agent.testing;

import java.util.concurrent.Callable;

import static org.junit.Assert.assertTrue;

public class Assert {
    public static void assertThrows(Callable lambda) {
        assertThrows(Throwable.class, lambda);
    }

    public static void assertThrows(Class<? extends Throwable> expectedErrorClass, Callable lambda) {
        Throwable caught = null;
        try {
            lambda.call();
        } catch (Throwable t) {
            caught = t;
        }
        assertTrue("nothing thrown when expecting " +
                expectedErrorClass.getName(), caught != null);
        assertTrue(caught.getClass().getName() + " (" + caught.getMessage() +
                        ") was thrown when expecting " + expectedErrorClass.getName(),
                expectedErrorClass.isAssignableFrom(caught.getClass()));
    }
}
