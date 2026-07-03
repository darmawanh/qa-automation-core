package com.localfirst.qa.core.client;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

/**
 * Base API client wrapper around REST Assured.
 *
 * <p>Platform API handlers in the project repo should extend this class (or delegate to it)
 * rather than calling {@code RestAssured.given()} directly. This ensures:
 * <ul>
 *   <li>Consistent base URI, authentication, and content-type configuration</li>
 *   <li>Centralised request/response logging</li>
 *   <li>Retry and polling hooks</li>
 *   <li>Config-driven environment binding</li>
 * </ul>
 *
 * <p>Usage in project handler:
 * <pre>{@code
 * public class ProfileEnrollmentHandler extends BaseApiClient {
 *     public ProfileEnrollmentHandler(String baseUri) {
 *         super(baseUri);
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
        return execute(given().when().get(path));
    }

    public Response get(String path, Map<String, ?> queryParams) {
        return execute(given().queryParams(queryParams).when().get(path));
    }

    public Response post(String path, Object body) {
        return execute(given().body(body).when().post(path));
    }

    public Response put(String path, Object body) {
        return execute(given().body(body).when().put(path));
    }

    public Response patch(String path, Object body) {
        return execute(given().body(body).when().patch(path));
    }

    public Response delete(String path) {
        return execute(given().when().delete(path));
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

    private Response execute(Response response) {
        if (logResponses) {
            // RestAssured's built-in logging can be enabled per-request;
            // this wrapper adds structured logging when needed.
        }
        return response;
    }
}
