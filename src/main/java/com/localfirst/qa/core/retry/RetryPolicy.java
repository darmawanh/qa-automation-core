package com.localfirst.qa.core.retry;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

/**
 * Configurable retry policy for flaky network or async-await scenarios.
 *
 * <p>Usage:
 * <pre>{@code
 * RetryPolicy.once()
 *     .maxAttempts(5)
 *     .withBackoff(Duration.ofMillis(500), Duration.ofSeconds(5))
 *     .until(response -> response.getStatusCode() == 200)
 *     .execute(() -> client.get("/healthz"));
 * }</pre>
 *
 * <p>Designed to replace {@code Thread.sleep} scattered in automation code.
 */
public final class RetryPolicy<T> {

    private int maxAttempts = 3;
    private Duration initialBackoff = Duration.ofSeconds(1);
    private Duration maxBackoff = Duration.ofSeconds(30);
    private double backoffMultiplier = 2.0;
    private Predicate<T> successPredicate;
    private Predicate<Exception> retryableException = e -> true;

    private RetryPolicy() {}

    /** Create a new retry policy. */
    public static <T> RetryPolicy<T> once() {
        return new RetryPolicy<>();
    }

    /** Maximum number of attempts (including the first). */
    public RetryPolicy<T> maxAttempts(int max) {
        this.maxAttempts = max;
        return this;
    }

    /** Initial delay between retries. */
    public RetryPolicy<T> withBackoff(Duration initial, Duration max) {
        this.initialBackoff = initial;
        this.maxBackoff = max;
        return this;
    }

    /** Retry while this predicate returns {@code false}. */
    public RetryPolicy<T> until(Predicate<T> successCondition) {
        this.successPredicate = successCondition;
        return this;
    }

    /** Only retry when the exception matches this predicate. */
    public RetryPolicy<T> retryOn(Predicate<Exception> retryable) {
        this.retryableException = retryable;
        return this;
    }

    /**
     * Execute the callable, retrying per policy.
     *
     * @throws RetryExhaustedException if all attempts are exhausted without success
     */
    public T execute(Callable<T> action) {
        long delayMs = initialBackoff.toMillis();
        Exception lastException = null;
        T lastResult = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                lastResult = action.call();
                if (successPredicate == null || successPredicate.test(lastResult)) {
                    return lastResult;
                }
            } catch (Exception e) {
                lastException = e;
                if (!retryableException.test(e)) {
                    throw new RetryExhaustedException(
                            "Non-retryable exception on attempt " + attempt, e);
                }
            }

            if (attempt < maxAttempts) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RetryExhaustedException("Retry interrupted", ie);
                }
                delayMs = Math.min(
                        (long) (delayMs * backoffMultiplier),
                        maxBackoff.toMillis());
            }
        }

        throw new RetryExhaustedException(
                "Exhausted " + maxAttempts + " retry attempts. Last result: " + lastResult,
                lastException);
    }
}
