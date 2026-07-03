package com.localfirst.qa.core.retry;

/**
 * Thrown when {@link PollingUtils#waitUntil} times out without the condition being met.
 */
public class PollingTimeoutException extends RuntimeException {

    public PollingTimeoutException(String message) {
        super(message);
    }

    public PollingTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
