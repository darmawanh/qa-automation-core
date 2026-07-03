package com.localfirst.qa.core.smoke;

import com.localfirst.qa.core.client.BaseApiClient;
import org.testng.annotations.Test;

import static com.localfirst.qa.core.assertion.AssertionFacade.assertThat;

/**
 * Framework smoke test — validates that the core framework compiles, resolves all
 * dependencies, and core classes instantiate correctly under the TestNG runner.
 *
 * <p>HTTP integration (REST Assured) is validated in TASK-QA-AUTO-02 against the
 * running Control Plane backend — not against external public echo services that
 * may rate-limit during repeated builds.
 */
public class CoreFrameworkSmokeTest {

    @Test(description = "Framework smoke: BaseApiClient instantiates with a base URI")
    public void frameworkSmoke_baseApiClient_instantiates() {
        BaseApiClient client = new BaseApiClient("http://localhost:8080");
        assertThat(client).isNotNull();
    }

    @Test(description = "Framework smoke: AssertionFacade string assertion works")
    public void frameworkSmoke_assertionFacade_stringAssertion() {
        assertThat("qa-automation-core").isNotNull().startsWith("qa-");
    }

    @Test(description = "Framework smoke: AssertionFacade boolean assertion works")
    public void frameworkSmoke_assertionFacade_booleanAssertion() {
        assertThat(true).isTrue();
        assertThat(1 + 1).isEqualTo(2);
    }

    @Test(description = "Framework smoke: RetryPolicy constructs with fluent API")
    public void frameworkSmoke_retryPolicy_constructs() {
        com.localfirst.qa.core.retry.RetryPolicy<Boolean> policy =
                com.localfirst.qa.core.retry.RetryPolicy.<Boolean>once()
                        .maxAttempts(3)
                        .until(result -> result);

        assertThat(policy).isNotNull();
    }

    @Test(description = "Framework smoke: FrameworkConfig resolves properties")
    public void frameworkSmoke_frameworkConfig_resolvesDefaults() {
        String baseUri = com.localfirst.qa.core.config.FrameworkConfig.get(
                com.localfirst.qa.core.config.FrameworkConfig.Keys.API_BASE_URI,
                "http://localhost:8080");

        assertThat(baseUri).isNotNull().startsWith("http");
    }

    @Test(description = "Framework smoke: AllureReportingConfig paths resolve")
    public void frameworkSmoke_allureReportingConfig_pathsResolve() {
        java.nio.file.Path allureDir = com.localfirst.qa.core.reporting.AllureReportingConfig.allureResultsDir();
        assertThat(allureDir).isNotNull();
        assertThat(allureDir.toString()).contains("allure-results");
    }

    @Test(description = "Framework smoke: PollingUtils class is loadable")
    public void frameworkSmoke_pollingUtils_classLoadable() {
        // Verify the class is on the classpath and loadable
        Class<?> pollingClass = com.localfirst.qa.core.retry.PollingUtils.class;
        assertThat(pollingClass).isNotNull();
        assertThat(pollingClass.getSimpleName()).isEqualTo("PollingUtils");
    }
}
