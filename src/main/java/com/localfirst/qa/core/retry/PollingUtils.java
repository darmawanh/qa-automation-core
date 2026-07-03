package com.localfirst.qa.core.retry;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

/**
 * Polling utilities for async/eventual-consistency scenarios.
 *
 * <p>Use when you need to wait for a condition to become true rather than retrying on failure.
 *
 * <p>Usage:
 * <pre>{@code
 * PollingUtils.waitUntil(
 *     () -> client.get("/status").jsonPath().getString("state"),
 *     "READY"::equals,
 *     Duration.ofSeconds(30),
 *     Duration.ofSeconds(1)
 * );
 * }</pre>
 */
public final class PollingUtils {

    private PollingUtils() {}

    /**
     * Poll the {@code supplier} until the result satisfies {@code condition}, or the timeout expires.
     *
     * @param supplier   the action to poll (must be idempotent)
     * @param condition  the condition the result must satisfy
     * @param timeout    maximum time to poll
     * @param interval   time between polls
     * @param <T>        result type
     * @return the first result that satisfies the condition
     * @throws PollingTimeoutException if the condition is never met before the timeout
     */
    public static <T> T waitUntil(
            Callable<T> supplier,
            Predicate<T> condition,
            Duration timeout,
            Duration interval) {

        long deadline = System.currentTimeMillis() + timeout.toMillis();
        T lastResult = null;
        Exception lastException = null;

        while (System.currentTimeMillis() < deadline) {
            try {
                lastResult = supplier.call();
                if (condition.test(lastResult)) {
                    return lastResult;
                }
            } catch (Exception e) {
                lastException = e;
            }

            try {
                Thread.sleep(interval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PollingTimeoutException("Polling interrupted", e);
            }
        }

        throw new PollingTimeoutException(
                "Condition not met within " + timeout.toSeconds() + "s. Last result: " + lastResult,
                lastException);
    }

    /**
     * Shortcut: poll every second until the condition is true or the timeout expires.
     */
    public static <T> T waitUntil(
            Callable<T> supplier,
            Predicate<T> condition,
            Duration timeout) {
        return waitUntil(supplier, condition, timeout, Duration.ofSeconds(1));
    }
}
