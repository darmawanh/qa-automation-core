package com.localfirst.qa.core.assertion;

import io.restassured.response.Response;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA assertion facade — the single entry point for all test assertions in automation code.
 *
 * <p><strong>Why a facade?</strong> Raw assertion imports ({@code org.assertj}, {@code org.testng.Assert},
 * {@code org.junit.Assert}) scattered across step definitions make it difficult to:
 * <ul>
 *   <li>Add custom failure messages consistently</li>
 *   <li>Attach evidence (screenshots, logs) on assertion failure</li>
 *   <li>Swap assertion libraries later</li>
 *   <li>Enforce platform-specific assertion conventions</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * import static com.localfirst.qa.core.assertion.AssertionFacade.assertThat;
 *
 * assertThat(response).hasStatusCode(200);
 * assertThat(response).hasJsonPath("status", "ok");
 * assertThat(response).responseTimeLessThan(3000);
 * }</pre>
 */
public final class AssertionFacade {

    private AssertionFacade() {
        // utility class — no instantiation
    }

    // ---- Entry points ----

    /** Assert on an HTTP response. */
    public static ResponseAssert assertThat(Response response) {
        return new ResponseAssert(response);
    }

    /** Assert on a plain String (e.g. response body). */
    public static org.assertj.core.api.AbstractStringAssert<?> assertThat(String actual) {
        return Assertions.assertThat(actual);
    }

    /** Assert on any object. */
    public static <T> org.assertj.core.api.ObjectAssert<T> assertThat(T actual) {
        return Assertions.assertThat(actual);
    }

    /** Assert on a boolean condition. */
    public static org.assertj.core.api.AbstractBooleanAssert<?> assertThat(boolean actual) {
        return Assertions.assertThat(actual);
    }

    /** Assert on an integer. */
    public static org.assertj.core.api.AbstractIntegerAssert<?> assertThat(int actual) {
        return Assertions.assertThat(actual);
    }

    /** Assert on a long (e.g. response time). */
    public static org.assertj.core.api.AbstractLongAssert<?> assertThat(long actual) {
        return Assertions.assertThat(actual);
    }

    /** Assert on a double. */
    public static org.assertj.core.api.AbstractDoubleAssert<?> assertThat(double actual) {
        return Assertions.assertThat(actual);
    }

    // ---- Specialised response assertions ----

    /**
     * Assertion class for REST Assured {@link Response} objects.
     */
    public static class ResponseAssert extends AbstractAssert<ResponseAssert, Response> {

        protected ResponseAssert(Response actual) {
            super(actual, ResponseAssert.class);
        }

        public ResponseAssert hasStatusCode(int expected) {
            isNotNull();
            assertThat(actual.getStatusCode())
                    .as("Expected HTTP %d but got %d. Body: %s",
                            expected, actual.getStatusCode(), bodyPreview())
                    .isEqualTo(expected);
            return this;
        }

        public ResponseAssert hasStatusCodeBetween(int min, int max) {
            isNotNull();
            assertThat(actual.getStatusCode())
                    .as("Expected status between %d and %d but got %d",
                            min, max, actual.getStatusCode())
                    .isBetween(min, max);
            return this;
        }

        public ResponseAssert hasJsonPath(String path, Object expectedValue) {
            isNotNull();
            Object actualValue = actual.jsonPath().get(path);
            assertThat(actualValue)
                    .as("JSON path '%s': expected '%s' but got '%s'", path, expectedValue, actualValue)
                    .isEqualTo(expectedValue);
            return this;
        }

        public ResponseAssert hasJsonPathPresent(String path) {
            isNotNull();
            Object value = actual.jsonPath().get(path);
            assertThat(value)
                    .as("JSON path '%s' should be present and non-null", path)
                    .isNotNull();
            return this;
        }

        public ResponseAssert hasJsonPathAbsent(String path) {
            isNotNull();
            Object value = actual.jsonPath().get(path);
            assertThat(value)
                    .as("JSON path '%s' should be absent (null)", path)
                    .isNull();
            return this;
        }

        public ResponseAssert hasContentType(String expectedContentType) {
            isNotNull();
            assertThat(actual.getContentType())
                    .as("Expected Content-Type '%s' but got '%s'",
                            expectedContentType, actual.getContentType())
                    .contains(expectedContentType);
            return this;
        }

        public ResponseAssert responseTimeLessThan(long maxMillis) {
            isNotNull();
            assertThat(actual.getTime())
                    .as("Response time %d ms exceeds maximum %d ms",
                            actual.getTime(), maxMillis)
                    .isLessThan(maxMillis);
            return this;
        }

        public ResponseAssert hasBodyContaining(String substring) {
            isNotNull();
            assertThat(actual.getBody().asString())
                    .as("Response body should contain '%s'", substring)
                    .contains(substring);
            return this;
        }

        public ResponseAssert hasBodyNotContaining(String substring) {
            isNotNull();
            assertThat(actual.getBody().asString())
                    .as("Response body should NOT contain '%s'", substring)
                    .doesNotContain(substring);
            return this;
        }

        public ResponseAssert hasJsonArrayOfSize(String path, int expectedSize) {
            isNotNull();
            int actualSize = actual.jsonPath().getList(path).size();
            assertThat(actualSize)
                    .as("JSON array at '%s' expected size %d but got %d", path, expectedSize, actualSize)
                    .isEqualTo(expectedSize);
            return this;
        }

        public ResponseAssert isJson() {
            isNotNull();
            String contentType = actual.getContentType();
            assertThat(contentType)
                    .as("Expected JSON Content-Type but got '%s'", contentType)
                    .contains("application/json");
            return this;
        }

        /**
         * Returns the response for further fluent use.
         */
        public Response andReturn() {
            return actual;
        }

        private String bodyPreview() {
            String body = actual.getBody().asString();
            if (body.length() > 500) {
                return body.substring(0, 500) + "... [truncated]";
            }
            return body;
        }
    }
}
