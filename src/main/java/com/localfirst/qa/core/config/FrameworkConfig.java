package com.localfirst.qa.core.config;

import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

/**
 * Centralised configuration for the QA automation framework.
 *
 * <p>Loads from (in priority order):
 * <ol>
 *   <li>System properties ({@code -Dqa.api.baseUri=...})</li>
 *   <li>Environment variables ({@code QA_API_BASE_URI=...})</li>
 *   <li>{@code qa-automation.properties} on the classpath</li>
 *   <li>Built-in defaults</li>
 * </ol>
 *
 * <p>Never hardcode URLs, credentials, or environment-specific values in test code.
 */
public final class FrameworkConfig {

    private static final String PROPS_FILE = "qa-automation.properties";
    private static FrameworkConfig instance;

    private final Properties properties;

    private FrameworkConfig() {
        this.properties = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(PROPS_FILE)) {
            if (in != null) {
                properties.load(in);
            }
        } catch (Exception e) {
            // properties file is optional; system props and env vars take precedence anyway
        }
    }

    private static FrameworkConfig getInstance() {
        if (instance == null) {
            instance = new FrameworkConfig();
        }
        return instance;
    }

    // ---- Public API ----

    /** Resolve a configuration value from the property chain. */
    public static String get(String key) {
        return get(key, null);
    }

    /** Resolve a configuration value with a default. */
    public static String get(String key, String defaultValue) {
        // 1. System property
        String value = System.getProperty(key);
        if (value != null) return value;

        // 2. Environment variable (dots → underscores, uppercased)
        String envKey = key.replace('.', '_').toUpperCase();
        value = System.getenv(envKey);
        if (value != null) return value;

        // 3. Properties file
        value = getInstance().properties.getProperty(key);
        if (value != null) return value;

        // 4. Default
        return defaultValue;
    }

    /** Resolve as int. */
    public static int getInt(String key, int defaultValue) {
        String v = get(key);
        return v != null ? Integer.parseInt(v) : defaultValue;
    }

    /** Resolve as boolean. */
    public static boolean getBoolean(String key, boolean defaultValue) {
        String v = get(key);
        return v != null ? Boolean.parseBoolean(v) : defaultValue;
    }

    // ---- Well-known keys ----

    public static final class Keys {
        public static final String API_BASE_URI = "qa.api.baseUri";
        public static final String API_AUTH_TOKEN = "qa.api.authToken";
        public static final String API_TIMEOUT_MS = "qa.api.timeoutMs";

        public static final String AIHUB_BASE_URI = "qa.aihub.baseUri";
        public static final String AIHUB_LOCAL_ENV = "qa.aihub.localEnv";

        public static final String MOBILE_PLATFORM = "qa.mobile.platform";
        public static final String MOBILE_APPIUM_URL = "qa.mobile.appiumUrl";
        public static final String MOBILE_DEVICE_NAME = "qa.mobile.deviceName";

        public static final String RETRY_MAX_ATTEMPTS = "qa.retry.maxAttempts";
        public static final String RETRY_INITIAL_BACKOFF_MS = "qa.retry.initialBackoffMs";
        public static final String RETRY_MAX_BACKOFF_MS = "qa.retry.maxBackoffMs";

        public static final String REPORT_OUTPUT_DIR = "qa.report.outputDir";

        private Keys() {}
    }
}
