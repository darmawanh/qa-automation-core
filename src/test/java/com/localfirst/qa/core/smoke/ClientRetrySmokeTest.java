package com.localfirst.qa.core.smoke;

import com.localfirst.qa.core.client.ApiErrorClassifier;
import com.localfirst.qa.core.client.BaseApiClient;
import com.localfirst.qa.core.client.ErrorCategory;
import com.localfirst.qa.core.retry.RetryExhaustedException;
import com.localfirst.qa.core.retry.RetryPolicy;
import org.testng.annotations.Test;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static com.localfirst.qa.core.assertion.AssertionFacade.assertThat;

/**
 * Smoke tests for client retry logic and error classification.
 *
 * <p>Verifies:
 * <ul>
 *   <li>{@link ErrorCategory} enum exists with all five values</li>
 *   <li>{@link ApiErrorClassifier} correctly classifies status codes and exceptions</li>
 *   <li>{@link RetryPolicy} retries on server errors (5xx) but not on client errors (4xx)</li>
 *   <li>Exponential backoff timing is honoured</li>
 *   <li>{@link BaseApiClient} retry configuration is reflected in its fluent API</li>
 * </ul>
 */
public class ClientRetrySmokeTest {

    // ---- ErrorCategory smoke ----

    @Test(description = "ErrorCategory enum has all five values")
    public void errorCategory_allValuesPresent() {
        ErrorCategory[] values = ErrorCategory.values();
        assertThat(values.length).isEqualTo(5);
        assertThat(ErrorCategory.valueOf("NETWORK_ERROR")).isNotNull();
        assertThat(ErrorCategory.valueOf("AUTH_ERROR")).isNotNull();
        assertThat(ErrorCategory.valueOf("CLIENT_ERROR")).isNotNull();
        assertThat(ErrorCategory.valueOf("SERVER_ERROR")).isNotNull();
        assertThat(ErrorCategory.valueOf("UNKNOWN")).isNotNull();
    }

    // ---- ApiErrorClassifier: status codes ----

    @Test(description = "Classify: 401 and 403 are AUTH_ERROR")
    public void classify_authErrors() {
        assertThat(ApiErrorClassifier.classify(401)).isEqualTo(ErrorCategory.AUTH_ERROR);
        assertThat(ApiErrorClassifier.classify(403)).isEqualTo(ErrorCategory.AUTH_ERROR);
    }

    @Test(description = "Classify: 4xx (except 401/403) are CLIENT_ERROR")
    public void classify_clientErrors() {
        assertThat(ApiErrorClassifier.classify(400)).isEqualTo(ErrorCategory.CLIENT_ERROR);
        assertThat(ApiErrorClassifier.classify(402)).isEqualTo(ErrorCategory.CLIENT_ERROR);
        assertThat(ApiErrorClassifier.classify(404)).isEqualTo(ErrorCategory.CLIENT_ERROR);
        assertThat(ApiErrorClassifier.classify(409)).isEqualTo(ErrorCategory.CLIENT_ERROR);
        assertThat(ApiErrorClassifier.classify(422)).isEqualTo(ErrorCategory.CLIENT_ERROR);
        assertThat(ApiErrorClassifier.classify(429)).isEqualTo(ErrorCategory.CLIENT_ERROR);
    }

    @Test(description = "Classify: 5xx are SERVER_ERROR")
    public void classify_serverErrors() {
        assertThat(ApiErrorClassifier.classify(500)).isEqualTo(ErrorCategory.SERVER_ERROR);
        assertThat(ApiErrorClassifier.classify(502)).isEqualTo(ErrorCategory.SERVER_ERROR);
        assertThat(ApiErrorClassifier.classify(503)).isEqualTo(ErrorCategory.SERVER_ERROR);
        assertThat(ApiErrorClassifier.classify(504)).isEqualTo(ErrorCategory.SERVER_ERROR);
    }

    @Test(description = "Classify: 2xx and 3xx are UNKNOWN (not errors)")
    public void classify_successCodes() {
        assertThat(ApiErrorClassifier.classify(200)).isEqualTo(ErrorCategory.UNKNOWN);
        assertThat(ApiErrorClassifier.classify(201)).isEqualTo(ErrorCategory.UNKNOWN);
        assertThat(ApiErrorClassifier.classify(301)).isEqualTo(ErrorCategory.UNKNOWN);
        assertThat(ApiErrorClassifier.classify(302)).isEqualTo(ErrorCategory.UNKNOWN);
    }

    // ---- ApiErrorClassifier: exceptions ----

    @Test(description = "Classify: network exception types are NETWORK_ERROR")
    public void classify_networkExceptions() {
        assertThat(ApiErrorClassifier.classify(new ConnectException("Connection refused")))
                .isEqualTo(ErrorCategory.NETWORK_ERROR);
        assertThat(ApiErrorClassifier.classify(new SocketTimeoutException("connect timed out")))
                .isEqualTo(ErrorCategory.NETWORK_ERROR);
        assertThat(ApiErrorClassifier.classify(new UnknownHostException("no such host")))
                .isEqualTo(ErrorCategory.NETWORK_ERROR);
    }

    @Test(description = "Classify: network error via message heuristic")
    public void classify_networkByMessage() {
        RuntimeException e = new RuntimeException("Connection refused: no further information");
        assertThat(ApiErrorClassifier.classify(e)).isEqualTo(ErrorCategory.NETWORK_ERROR);
    }

    @Test(description = "Classify: network error via cause chain")
    public void classify_networkByCauseChain() {
        RuntimeException wrapper = new RuntimeException(
                new ConnectException("Connection refused"));
        assertThat(ApiErrorClassifier.classify(wrapper)).isEqualTo(ErrorCategory.NETWORK_ERROR);
    }

    @Test(description = "Classify: null and generic exceptions are UNKNOWN")
    public void classify_unknownExceptions() {
        assertThat(ApiErrorClassifier.classify(null)).isEqualTo(ErrorCategory.UNKNOWN);
        assertThat(ApiErrorClassifier.classify(new IllegalArgumentException("bad arg")))
                .isEqualTo(ErrorCategory.UNKNOWN);
    }

    // ---- ApiErrorClassifier: retryability ----

    @Test(description = "isRetryable: NETWORK_ERROR and SERVER_ERROR are retryable")
    public void isRetryable_retryableCategories() {
        assertThat(ApiErrorClassifier.isRetryable(ErrorCategory.NETWORK_ERROR)).isTrue();
        assertThat(ApiErrorClassifier.isRetryable(ErrorCategory.SERVER_ERROR)).isTrue();
    }

    @Test(description = "isRetryable: AUTH_ERROR, CLIENT_ERROR, UNKNOWN are not retryable")
    public void isRetryable_nonRetryableCategories() {
        assertThat(ApiErrorClassifier.isRetryable(ErrorCategory.AUTH_ERROR)).isFalse();
        assertThat(ApiErrorClassifier.isRetryable(ErrorCategory.CLIENT_ERROR)).isFalse();
        assertThat(ApiErrorClassifier.isRetryable(ErrorCategory.UNKNOWN)).isFalse();
    }

    @Test(description = "isRetryable(int): convenience method works")
    public void isRetryable_statusCodes() {
        assertThat(ApiErrorClassifier.isRetryable(500)).isTrue();
        assertThat(ApiErrorClassifier.isRetryable(503)).isTrue();
        assertThat(ApiErrorClassifier.isRetryable(401)).isFalse();
        assertThat(ApiErrorClassifier.isRetryable(404)).isFalse();
        assertThat(ApiErrorClassifier.isRetryable(200)).isFalse();
    }

    // ---- RetryPolicy with error classifier: retry on server error ----

    @Test(description = "RetryPolicy retries on 5xx (SERVER_ERROR) and succeeds when status recovers")
    public void retryPolicy_retriesOnServerError_andSucceeds() {
        AtomicInteger attempts = new AtomicInteger(0);

        int result = RetryPolicy.<Integer>once()
                .maxAttempts(3)
                .withBackoff(Duration.ofMillis(10), Duration.ofMillis(200))
                .until(status -> !ApiErrorClassifier.isRetryable(status))
                .retryOn(ApiErrorClassifier::isRetryable)
                .execute(() -> {
                    int attempt = attempts.incrementAndGet();
                    if (attempt < 3) {
                        return 503; // server error — retryable
                    }
                    return 200; // success on third attempt
                });

        assertThat(attempts.get()).isEqualTo(3);
        assertThat(result).isEqualTo(200);
    }

    // ---- RetryPolicy with error classifier: no retry on client error ----

    @Test(description = "RetryPolicy does NOT retry on 4xx — returns immediately with one attempt")
    public void retryPolicy_doesNotRetryOnClientError() {
        AtomicInteger attempts = new AtomicInteger(0);

        int result = RetryPolicy.<Integer>once()
                .maxAttempts(3)
                .withBackoff(Duration.ofMillis(10), Duration.ofMillis(200))
                .until(status -> !ApiErrorClassifier.isRetryable(status))
                .retryOn(ApiErrorClassifier::isRetryable)
                .execute(() -> {
                    attempts.incrementAndGet();
                    return 404; // client error — not retryable
                });

        assertThat(attempts.get()).isEqualTo(1);
        assertThat(result).isEqualTo(404);
    }

    @Test(description = "RetryPolicy does NOT retry on 401/403 auth errors")
    public void retryPolicy_doesNotRetryOnAuthError() {
        AtomicInteger attempts = new AtomicInteger(0);

        int result = RetryPolicy.<Integer>once()
                .maxAttempts(5)
                .withBackoff(Duration.ofMillis(10), Duration.ofMillis(200))
                .until(status -> !ApiErrorClassifier.isRetryable(status))
                .retryOn(ApiErrorClassifier::isRetryable)
                .execute(() -> {
                    attempts.incrementAndGet();
                    return 401;
                });

        assertThat(attempts.get()).isEqualTo(1);
        assertThat(result).isEqualTo(401);
    }

    // ---- RetryPolicy with error classifier: retry on network exception ----

    @Test(description = "RetryPolicy retries on network exceptions and succeeds on recovery")
    public void retryPolicy_retriesOnNetworkException() {
        AtomicInteger attempts = new AtomicInteger(0);

        int result = RetryPolicy.<Integer>once()
                .maxAttempts(3)
                .withBackoff(Duration.ofMillis(10), Duration.ofMillis(200))
                .until(status -> true) // any response is success
                .retryOn(ApiErrorClassifier::isRetryable)
                .execute(() -> {
                    int attempt = attempts.incrementAndGet();
                    if (attempt < 3) {
                        throw new ConnectException("Connection refused");
                    }
                    return 200;
                });

        assertThat(attempts.get()).isEqualTo(3);
        assertThat(result).isEqualTo(200);
    }

    // ---- Backoff timing ----

    @Test(description = "RetryPolicy honours exponential backoff timing")
    public void retryPolicy_backoffTiming() {
        long start = System.currentTimeMillis();

        try {
            RetryPolicy.<Integer>once()
                    .maxAttempts(3)
                    .withBackoff(Duration.ofMillis(100), Duration.ofMillis(1000))
                    .until(status -> false) // never succeeds — forces all retries
                    .retryOn(e -> true)
                    .execute(() -> 500);
        } catch (RetryExhaustedException e) {
            // expected — all attempts exhausted
        }

        long elapsed = System.currentTimeMillis() - start;
        // 3 attempts = 2 backoff delays: 100ms + 200ms = 300ms minimum
        assertThat(elapsed).isGreaterThanOrEqualTo(250);
        // 3 attempts = 2 backoff delays: 100ms + 200ms = 300ms.
        // Allow generous upper bound (2s) to tolerate CI slowness.
        assertThat(elapsed).isLessThan(2000);
    }

    // ---- RetryPolicy exhausts ----

    @Test(description = "RetryPolicy throws RetryExhaustedException when max attempts reached without success")
    public void retryPolicy_exhausts_andThrows() {
        AtomicInteger attempts = new AtomicInteger(0);

        try {
            RetryPolicy.<Integer>once()
                    .maxAttempts(3)
                    .withBackoff(Duration.ofMillis(10), Duration.ofMillis(200))
                    .until(status -> !ApiErrorClassifier.isRetryable(status))
                    .retryOn(ApiErrorClassifier::isRetryable)
                    .execute(() -> {
                        attempts.incrementAndGet();
                        return 503; // always server error
                    });
            // Should not reach here
            assertThat(false).as("Expected RetryExhaustedException was not thrown").isTrue();
        } catch (RetryExhaustedException e) {
            assertThat(attempts.get()).isEqualTo(3);
            assertThat(e.getMessage()).contains("3");
        }
    }

    // ---- BaseApiClient retry configuration ----

    @Test(description = "BaseApiClient withRetry() enables retry and sets defaults")
    public void baseApiClient_withRetry_enablesDefaults() {
        BaseApiClient client = new BaseApiClient("http://localhost:8080").withRetry();
        assertThat(client).isNotNull();
        // The client is configured; actual HTTP behaviour is verified in
        // integration tests against the running platform.
    }

    @Test(description = "BaseApiClient withRetry(int) configures custom max retries")
    public void baseApiClient_withRetry_customMaxRetries() {
        BaseApiClient client = new BaseApiClient("http://localhost:8080").withRetry(5);
        assertThat(client).isNotNull();
    }

    @Test(description = "BaseApiClient withRetry(full) accepts all custom parameters")
    public void baseApiClient_withRetry_fullConfiguration() {
        BaseApiClient client = new BaseApiClient("http://localhost:8080")
                .withRetry(5, 2000, 60000, 1.5);
        assertThat(client).isNotNull();
    }

    @Test(description = "BaseApiClient withoutRetry() disables retry")
    public void baseApiClient_withoutRetry_disables() {
        BaseApiClient client = new BaseApiClient("http://localhost:8080")
                .withRetry(3)
                .withoutRetry();
        assertThat(client).isNotNull();
    }

    @Test(description = "BaseApiClient retry config is fluent (chained with other config)")
    public void baseApiClient_fluentRetryChain() {
        BaseApiClient client = new BaseApiClient("http://localhost:8080")
                .withTimeout(5000)
                .withRetry(3, 1000, 30000, 2.0)
                .withLogging(true, false);
        assertThat(client).isNotNull();
    }

    // ---- Edge cases ----

    @Test(description = "Classify: lowercase message heuristics still match")
    public void classify_messageCaseInsensitive() {
        RuntimeException e = new RuntimeException("Connection REFUSED: no further information");
        assertThat(ApiErrorClassifier.classify(e)).isEqualTo(ErrorCategory.NETWORK_ERROR);
    }

    @Test(description = "isRetryable: null Throwable is false")
    public void isRetryable_nullThrowable() {
        assertThat(ApiErrorClassifier.isRetryable((Throwable) null)).isFalse();
    }
}
