package com.localfirst.qa.core.reporting;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Centralised Allure + Cucumber reporting configuration.
 *
 * <p>Sets system properties for Allure and Cucumber report output directories.
 * Call {@link #configure()} once at framework bootstrap (e.g. in a
 * {@code @BeforeSuite} hook or a Cucumber plugin).
 *
 * <p>Report output paths:
 * <ul>
 *   <li>Allure results: {@code build/allure-results/} (default)</li>
 *   <li>Cucumber reports: {@code build/cucumber-reports/} (default)</li>
 * </ul>
 *
 * <p>Override via:
 * <ul>
 *   <li>System property: {@code -Dqa.report.outputDir=target/reports}</li>
 *   <li>Or call {@link #configure(Path)} with a custom base directory</li>
 * </ul>
 */
public final class AllureReportingConfig {

    private static final String DEFAULT_OUTPUT_DIR = "build";
    private static boolean configured = false;

    private AllureReportingConfig() {}

    /** Configure with default output paths (under {@code build/}). */
    public static synchronized void configure() {
        configure(Paths.get(DEFAULT_OUTPUT_DIR));
    }

    /** Configure with a custom output base directory. */
    public static synchronized void configure(Path outputBase) {
        if (configured) return;
        configured = true;

        File allureDir = outputBase.resolve("allure-results").toFile();
        File cucumberDir = outputBase.resolve("cucumber-reports").toFile();

        allureDir.mkdirs();
        cucumberDir.mkdirs();

        // Allure reads this property to know where to write results
        System.setProperty("allure.results.directory", allureDir.getAbsolutePath());
    }

    /** Returns the Allure results directory path. */
    public static Path allureResultsDir() {
        String dir = System.getProperty("allure.results.directory");
        return dir != null ? Paths.get(dir) : Paths.get(DEFAULT_OUTPUT_DIR, "allure-results");
    }
}
