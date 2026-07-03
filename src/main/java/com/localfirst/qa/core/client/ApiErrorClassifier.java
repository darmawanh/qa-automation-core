package com.localfirst.qa.core.client;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.PortUnreachableException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLException;

/**
 * Classifies exceptions and HTTP status codes into {@link ErrorCategory} values
 * and provides a consistent retryability decision for the framework.
 *
 * <h3>Classification rules</h3>
 * <table>
 *   <tr><th>Input</th><th>Category</th><th>Retryable?</th></tr>
 *   <tr><td>401, 403</td><td>{@link ErrorCategory#AUTH_ERROR}</td><td>No</td></tr>
 *   <tr><td>400, 402, 404–499</td><td>{@link ErrorCategory#CLIENT_ERROR}</td><td>No</td></tr>
 *   <tr><td>500–599</td><td>{@link ErrorCategory#SERVER_ERROR}</td><td>Yes</td></tr>
 *   <tr><td>Connection/timeout/SSL exceptions</td><td>{@link ErrorCategory#NETWORK_ERROR}</td><td>Yes</td></tr>
 *   <tr><td>Everything else</td><td>{@link ErrorCategory#UNKNOWN}</td><td>No</td></tr>
 * </table>
 *
 * <p>When classifying a {@link Throwable}, the utility walks the cause chain
 * so that wrapped exceptions (e.g. {@code RuntimeException} wrapping a
 * {@code ConnectException}) are correctly identified as network errors.
 */
public final class ApiErrorClassifier {

    private ApiErrorClassifier() {
        // utility class — no instantiation
    }

    // ---- Status-code classification ----

    /**
     * Classify an HTTP status code.
     *
     * @param statusCode the HTTP status code
     * @return the corresponding error category
     */
    public static ErrorCategory classify(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return ErrorCategory.AUTH_ERROR;
        }
        if (statusCode >= 400 && statusCode < 500) {
            return ErrorCategory.CLIENT_ERROR;
        }
        if (statusCode >= 500 && statusCode < 600) {
            return ErrorCategory.SERVER_ERROR;
        }
        return ErrorCategory.UNKNOWN;
    }

    // ---- Exception classification ----

    /**
     * Classify a throwable by inspecting its type, message, and cause chain.
     *
     * @param t the throwable; may be {@code null}
     * @return the corresponding error category
     */
    public static ErrorCategory classify(Throwable t) {
        if (t == null) {
            return ErrorCategory.UNKNOWN;
        }

        // Direct type checks for common JDK network exceptions
        if (t instanceof ConnectException
                || t instanceof SocketTimeoutException
                || t instanceof UnknownHostException
                || t instanceof NoRouteToHostException
                || t instanceof PortUnreachableException
                || t instanceof SSLException) {
            return ErrorCategory.NETWORK_ERROR;
        }

        // Class-name heuristics for library-specific exceptions that may not
        // be on the compile classpath (e.g. Apache HttpClient, OkHttp)
        String className = t.getClass().getName();
        if (className.contains("SocketException")
                || className.contains("HttpHostConnectException")
                || className.contains("ConnectTimeoutException")) {
            return ErrorCategory.NETWORK_ERROR;
        }

        // Message heuristics for exceptions whose type we cannot check at
        // compile time (REST Assured wraps connection errors in RuntimeException)
        String msg = t.getMessage();
        if (msg != null) {
            String lower = msg.toLowerCase();
            if (lower.contains("connection refused")
                    || lower.contains("connect timed out")
                    || lower.contains("read timed out")
                    || lower.contains("no route to host")
                    || lower.contains("network is unreachable")
                    || lower.contains("unable to resolve host")) {
                return ErrorCategory.NETWORK_ERROR;
            }
        }

        // Walk the cause chain — a RuntimeException wrapping a ConnectException
        // should be classified the same as the inner exception.
        Throwable cause = t.getCause();
        if (cause != null && cause != t) {
            return classify(cause);
        }

        return ErrorCategory.UNKNOWN;
    }

    // ---- Retryability ----

    /**
     * Returns {@code true} if the given category is safe to retry automatically.
     *
     * <p>Retryable categories:
     * <ul>
     *   <li>{@link ErrorCategory#NETWORK_ERROR} — transient infrastructure failure</li>
     *   <li>{@link ErrorCategory#SERVER_ERROR} — transient server failure</li>
     * </ul>
     */
    public static boolean isRetryable(ErrorCategory category) {
        return category == ErrorCategory.NETWORK_ERROR
                || category == ErrorCategory.SERVER_ERROR;
    }

    /**
     * Convenience: classify an HTTP status code and test retryability in one call.
     */
    public static boolean isRetryable(int statusCode) {
        return isRetryable(classify(statusCode));
    }

    /**
     * Convenience: classify a throwable and test retryability in one call.
     */
    public static boolean isRetryable(Throwable t) {
        return isRetryable(classify(t));
    }
}
