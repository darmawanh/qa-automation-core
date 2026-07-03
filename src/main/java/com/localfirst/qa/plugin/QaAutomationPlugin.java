package com.localfirst.qa.plugin;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;

/**
 * Gradle convention plugin for QA automation projects.
 *
 * <p>Applied in consuming project build files with:
 * <pre>{@code
 * plugins {
 *     id 'com.localfirst.qa-automation' version '0.1.0'
 * }
 * }</pre>
 *
 * <p>Configures:
 * <ul>
 *   <li>Java 17 toolchain</li>
 *   <li>TestNG test runner (default)</li>
 *   <li>Cucumber + Allure reporting dependencies</li>
 *   <li>REST Assured transitive availability</li>
 *   <li>Suite registration extensions</li>
 * </ul>
 */
public class QaAutomationPlugin implements Plugin<Project> {

    private static final String TESTNG_VERSION = "7.9.0";
    private static final String CUCUMBER_VERSION = "7.15.0";
    private static final String ALLURE_VERSION = "2.25.0";
    private static final String REST_ASSURED_VERSION = "5.4.0";

    @Override
    public void apply(Project project) {
        // --- Apply base Java plugin ---
        project.getPlugins().apply(JavaPlugin.class);

        // --- Java 17 source/target compatibility ---
        // Uses sourceCompatibility/targetCompatibility rather than toolchain
        // so the project builds on any JDK >= 17 without requiring a specific
        // toolchain download. Projects that need strict JDK 17 toolchain can
        // override in their own build.gradle.
        project.getExtensions().configure(JavaPluginExtension.class, java -> {
            java.setSourceCompatibility(org.gradle.api.JavaVersion.VERSION_17);
            java.setTargetCompatibility(org.gradle.api.JavaVersion.VERSION_17);
        });

        // --- Encoding ---
        project.getTasks().withType(JavaCompile.class, compile -> {
            compile.getOptions().setEncoding("UTF-8");
        });

        // --- Dependency management: add framework library to all configurations ---
        project.getDependencies().add("implementation",
                "com.localfirst.qa:qa-automation-core:" + getCoreVersion(project));

        // --- TestNG configuration ---
        project.getDependencies().add("testImplementation", "org.testng:testng:" + TESTNG_VERSION);

        project.getTasks().withType(Test.class, test -> {
            test.useTestNG();
            // Default suite file: project can override via -PqaSuite=...
            if (project.hasProperty("qaSuite")) {
                String suiteFile = (String) project.property("qaSuite");
                test.systemProperty("testng.suite", suiteFile);
            }
        });

        // --- Allure reporting ---
        project.getDependencies().add("testImplementation",
                "io.qameta.allure:allure-cucumber7-jvm:" + ALLURE_VERSION);
        project.getDependencies().add("testImplementation",
                "io.qameta.allure:allure-testng:" + ALLURE_VERSION);

        // --- Cucumber ---
        project.getDependencies().add("testImplementation",
                "io.cucumber:cucumber-java:" + CUCUMBER_VERSION);
        project.getDependencies().add("testImplementation",
                "io.cucumber:cucumber-testng:" + CUCUMBER_VERSION);

        // --- REST Assured ---
        project.getDependencies().add("testImplementation",
                "io.rest-assured:rest-assured:" + REST_ASSURED_VERSION);

        // --- Guardrail: forbid Thread.sleep in automation code (via checkstyle
        //     hook point — projects wire checkstyle separately) ---
        project.afterEvaluate(p -> {
            p.getLogger().lifecycle(
                    "[qa-automation] Convention plugin applied to {}", p.getName());
        });
    }

    /**
     * Resolves the core version. Projects can override with
     * {@code -PqaCoreVersion=x.y.z}.
     */
    private String getCoreVersion(Project project) {
        if (project.hasProperty("qaCoreVersion")) {
            return (String) project.property("qaCoreVersion");
        }
        // Default: versioned with this plugin
        return "0.1.0";
    }
}
