package com.localfirst.qa.core.client;

import com.localfirst.qa.core.config.FrameworkConfig;
import com.localfirst.qa.core.retry.RetryPolicy;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Base API client wrapper around REST Assured.
 *
 * <p>Platform API handlers in the project repo should extend this class (or delegate to it)
 * rather than calling {@code RestAssured.given()} directly. This ensures:
 * <ul>
 *   <li>Consistent base URI, authentication, and content-type configuration</li>
 *   <li>Centralised request/response logging</li>
 *   <li>Configurable retry with exponential backoff (see {@link #withRetry()})</li>
 *   <li>Error classification via {@link ApiErrorClassifier}</li>
 *   <li>Config-driven environment binding</li>
 * </ul>
 *
 * <h3>Retry behaviour</h3>
 * <p>When retry is enabled (via {@link #withRetry()} or its overloads), the client
 * will automatically retry HTTP calls that fail with:
 * <ul>
 *   <li>Network-level errors (connection refused, DNS failure, timeout)</li>
 *   <li>5xx server errors (500, 502, 503, 504)</li>
 * </ul>
 * It will <strong>not</strong> retry on:
 * <ul>
 *   <li>4xx client errors (400, 401, 403, 404, etc.)</li>
 *   <li>Authentication failures (401, 403)</li>
 * </ul>
 *
 * <p>Usage in project handler:
 * <pre>{@code
 * public class ProfileEnrollmentHandler extends BaseApiClient {
 *     public ProfileEnrollmentHandler(String baseUri) {
 *         super(baseUri);
 *         withRetry();  // enable retry with defaults
 *     }
 *
 *     public Response createSession(Object body) {
 *         return post("/api/v1/profile-enrollment/sessions", body);
 *     }
 * }
 * }</pre>
 */
public class BaseApiClient {

    protected final String baseUri;

    private ContentType defaultContentType = ContentType.JSON;
    private boolean logRequests = true;
    private boolean logResponses = true;
    private int defaultTimeout = 30000;

    // Retry configuration
    private boolean retryEnabled = false;
    private int maxRetries = 3;
    private long retryBackoffMs = 1000;
    private long retryMaxBackoffMs = 30000;
    private double retryBackoffMultiplier = 2.0;

    public BaseApiClient(String baseUri) {
        this.baseUri = baseUri;
    }

    // ---- Configuration ----

    public BaseApiClient withContentType(ContentType contentType) {
        this.defaultContentType = contentType;
        return this;
    }

    public BaseApiClient withLogging(boolean requests, boolean responses) {
        this.logRequests = requests;
        this.logResponses = responses;
        return this;
    }

    public BaseApiClient withTimeout(int timeoutMs) {
        this.defaultTimeout = timeoutMs;
        return this;
    }

    // ---- Retry configuration ----

    /**
     * Enable retry with defaults from {@link FrameworkConfig} (or built-in
     * defaults: 3 attempts, 1s initial backoff, 30s max, 2x multiplier).
     */
    public BaseApiClient withRetry() {
        return withRetry(
                FrameworkConfig.getInt(FrameworkConfig.Keys.RETRY_MAX_ATTEMPTS, 3),
                FrameworkConfig.getInt(FrameworkConfig.Keys.RETRY_INITIAL_BACKOFF_MS, 1000),
                FrameworkConfig.getInt(FrameworkConfig.Keys.RETRY_MAX_BACKOFF_MS, 30000),
                2.0);
    }

    /**
     * Enable retry with a custom maximum number of attempts (including the first).
     * Backoff defaults: 1s initial, 30s max, 2x multiplier.
     */
    public BaseApiClient withRetry(int maxRetries) {
        return withRetry(maxRetries, 1000, 30000, 2.0);
    }

    /**
     * Enable retry with full customisation.
     *
     * @param maxRetries        maximum attempts (including the first); must be >= 1
     * @param initialBackoffMs  delay before the first retry, in milliseconds
     * @param maxBackoffMs      maximum delay between retries, in milliseconds
     * @param multiplier        backoff multiplier applied after each attempt
     */
    public BaseApiClient withRetry(int maxRetries, long initialBackoffMs,
                                   long maxBackoffMs, double multiplier) {
        this.retryEnabled = true;
        this.maxRetries = Math.max(1, maxRetries);
        this.retryBackoffMs = initialBackoffMs;
        this.retryMaxBackoffMs = maxBackoffMs;
        this.retryBackoffMultiplier = multiplier;
        return this;
    }

    /**
     * Disable retry (the default).
     */
    public BaseApiClient withoutRetry() {
        this.retryEnabled = false;
        return this;
    }

    // ---- Request builders ----

    /**
     * Returns a pre-configured request specification with base URI and defaults.
     */
    public RequestSpecification given() {
        return RestAssured.given()
                .baseUri(baseUri)
                .contentType(defaultContentType)
                .accept(defaultContentType);
    }

    // ---- HTTP verbs ----

    public Response get(String path) {
        return execute(() -> given().when().get(path));
    }

    public Response get(String path, Map<String, ?> queryParams) {
        return execute(() -> given().queryParams(queryParams).when().get(path));
    }

    public Response post(String path, Object body) {
        return execute(() -> given().body(body).when().post(path));
    }

    public Response put(String path, Object body) {
        return execute(() -> given().body(body).when().put(path));
    }

    public Response patch(String path, Object body) {
        return execute(() -> given().body(body).when().patch(path));
    }

    public Response delete(String path) {
        return execute(() -> given().when().delete(path));
    }

    // ---- Auth helpers ----

    /**
     * Creates a pre-authenticated request with a Bearer token.
     */
    public RequestSpecification givenAuth(String bearerToken) {
        return given().header("Authorization", "Bearer " + bearerToken);
    }

    /**
     * Creates a request with a custom API key header.
     */
    public RequestSpecification givenApiKey(String headerName, String apiKey) {
        return given().header(headerName, apiKey);
    }

    // ---- Internal ----

    /**
     * Execute an HTTP call, optionally with retry.
     *
     * <p>When {@link #retryEnabled} is {@code true}, the call is wrapped in a
     * {@link RetryPolicy} that retries on network errors and 5xx server errors
     * with exponential backoff. Client errors (4xx) and auth errors (401/403)
     * are returned immediately without retry.
     */
    private Response execute(Callable<Response> httpCall) {
        if (!retryEnabled) {
            try {
                return httpCall.call();
            } catch (Exception e) {
                throw new RuntimeException("HTTP call failed: " + e.getMessage(), e);
            }
        }

        return RetryPolicy.<Response>once()
                .maxAttempts(maxRetries)
                .withBackoff(
                        Duration.ofMillis(retryBackoffMs),
                        Duration.ofMillis(retryMaxBackoffMs))
                .until(response -> !ApiErrorClassifier.isRetryable(response.getStatusCode()))
                .retryOn(ApiErrorClassifier::isRetryable)
                .execute(httpCall::call);
    }
}
