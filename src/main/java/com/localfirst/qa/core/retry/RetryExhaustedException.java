package com.localfirst.qa.core.retry;

/**
 * Thrown when a {@link RetryPolicy} exhausts all attempts without meeting its success condition.
 */
public class RetryExhaustedException extends RuntimeException {

    public RetryExhaustedException(String message) {
        super(message);
    }

    public RetryExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}
