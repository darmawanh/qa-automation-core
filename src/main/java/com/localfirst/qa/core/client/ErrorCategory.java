package com.localfirst.qa.core.client;

/**
 * Classification of API errors for retry-decision and error-reporting purposes.
 *
 * <p>Used by {@link ApiErrorClassifier} to categorise exceptions and HTTP status
 * codes, and consumed by {@link BaseApiClient} retry logic to decide whether
 * a failed call should be retried.
 *
 * <p>Categories:
 * <ul>
 *   <li>{@link #NETWORK_ERROR} — connection refused, DNS resolution failure,
 *       socket timeout, SSL handshake failure</li>
 *   <li>{@link #AUTH_ERROR} — HTTP 401 Unauthorized, 403 Forbidden</li>
 *   <li>{@link #CLIENT_ERROR} — HTTP 4xx except auth (400, 404, 409, 422, 429, etc.)</li>
 *   <li>{@link #SERVER_ERROR} — HTTP 5xx (500, 502, 503, 504, etc.)</li>
 *   <li>{@link #UNKNOWN} — anything else</li>
 * </ul>
 */
public enum ErrorCategory {

    /** Connection-level failures: DNS, timeout, refused, SSL. Retryable. */
    NETWORK_ERROR,

    /** HTTP 401 / 403 — authentication or authorisation failure. Not retryable. */
    AUTH_ERROR,

    /** HTTP 4xx (except 401/403) — client-side request error. Not retryable. */
    CLIENT_ERROR,

    /** HTTP 5xx — server-side error. Retryable. */
    SERVER_ERROR,

    /** Unclassified error. Not retryable by default. */
    UNKNOWN
}
