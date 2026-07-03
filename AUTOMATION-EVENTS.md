# Automation Events Log

Central evidence log for QA automation activities within qa-automation-core.

---

## 2026-07-03 — Framework Hardening: Retry + Error Classification (TASK-QA-AUTO-05)

**Session:** Sprint 16, Session 5
**Agent:** local-first-test-automation-architect
**Repo:** `qa-automation-core` (this repo)

### What was done

1. **ErrorCategory enum created:**
   - File: `src/main/java/.../client/ErrorCategory.java`
   - Five categories: `NETWORK_ERROR`, `AUTH_ERROR`, `CLIENT_ERROR`, `SERVER_ERROR`, `UNKNOWN`

2. **ApiErrorClassifier utility created:**
   - File: `src/main/java/.../client/ApiErrorClassifier.java`
   - Classifies exceptions and HTTP status codes into `ErrorCategory`
   - Walks cause chain for wrapped exceptions (e.g., `RuntimeException` wrapping `ConnectException`)
   - Message heuristics for library-specific exceptions not on compile classpath
   - `isRetryable()` convenience methods for category, int (status code), and Throwable

3. **BaseApiClient retry support added:**
   - File: `src/main/java/.../client/BaseApiClient.java`
   - `withRetry()` — enable retry with config-driven defaults (from `FrameworkConfig`)
   - `withRetry(int maxRetries)` — custom retry count
   - `withRetry(int maxRetries, long initialBackoffMs, long maxBackoffMs, double multiplier)` — full customisation
   - `withoutRetry()` — disable retry
   - Retry uses existing `RetryPolicy<T>` class with exponential backoff
   - Retries on: network errors, 5xx server errors
   - Does NOT retry on: 4xx client errors, 401/403 auth errors
   - Uses `ApiErrorClassifier` for retry decisions
   - All HTTP verbs (`get`, `post`, `put`, `patch`, `delete`) automatically covered

4. **ClientRetrySmokeTest created:**
   - File: `src/test/java/.../smoke/ClientRetrySmokeTest.java`
   - 25 TestNG tests covering:
     - `ErrorCategory` enum integrity
     - `ApiErrorClassifier` status code classification (AUTH_ERROR, CLIENT_ERROR, SERVER_ERROR, UNKNOWN)
     - `ApiErrorClassifier` exception classification (network exceptions, message heuristics, cause chain)
     - Retryability decisions (`isRetryable`)
     - Retry on server error (5xx) — verifies retry and eventual success
     - No retry on client error (4xx) — verifies single attempt
     - No retry on auth error (401/403)
     - Retry on network exceptions — verifies recovery
     - Exponential backoff timing honoured
     - `RetryExhaustedException` thrown when max attempts exhausted
     - `BaseApiClient` fluent retry configuration (all overloads)
     - Edge cases: case-insensitive message matching, null throwable

### Test results

- Total: 32 tests (25 new + 7 existing), 0 failures, 0 errors
- `ClientRetrySmokeTest`: 25 tests, 0.659s
- `CoreFrameworkSmokeTest`: 7 tests, 0.014s

### Key decisions

- DEC-SPRINT-16-011: Error classification lives in `com.localfirst.qa.core.client` package alongside `BaseApiClient` — not in the existing `retry` package — because it is a client concern, not a general retry utility.
- DEC-SPRINT-16-012: `ApiErrorClassifier` uses message heuristics in addition to type checks to handle library-specific exceptions (e.g., REST Assured wraps connection errors in plain `RuntimeException`).
- DEC-SPRINT-16-013: `BaseApiClient` retry delegates to the existing `RetryPolicy<T>` class rather than duplicating retry loop logic — all HTTP verbs benefit from the same retry behaviour.
- DEC-SPRINT-16-014: Retry is opt-in (`withRetry()`) rather than on by default — avoids breaking existing handlers that may not expect automatic retries.

### Repo state

- `qa-automation-core` master: 4 new/modified files committed (see commit hash).

---

*Template: Append new entries at top. Format: `## YYYY-MM-DD — Title`.*
