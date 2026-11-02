package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner;

import jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model.ExecutionReport;
import lombok.extern.slf4j.Slf4j;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class OopJUnit5TestRunner {

    public ExecutionReport.UnitTestPhase runTests(
            Path studentClassesDir,
            Path testClassesDir,
            List<String> testClassNames,
            int timeoutSecondsPerTest,
            double weight) {

        if (testClassNames == null || testClassNames.isEmpty()) {
            return ExecutionReport.UnitTestPhase.builder()
                    .score(100.0)
                    .weight(weight)
                    .weightedScore(Math.round(100.0 * weight * 10.0) / 10.0)
                    .passedCount(0)
                    .totalCount(0)
                    .failures(Collections.emptyList())
                    .build();
        }

        List<URL> urls = new ArrayList<>();
        try {
            if (studentClassesDir != null && Files.exists(studentClassesDir)) {
                urls.add(studentClassesDir.toUri().toURL());
            }
            if (testClassesDir != null && Files.exists(testClassesDir)) {
                urls.add(testClassesDir.toUri().toURL());
            }
        } catch (Exception e) {
            log.error("Failed to construct URLs for JUnit 5 ClassLoader", e);
            ExecutionReport.UnitTestFailure fail = ExecutionReport.UnitTestFailure.builder()
                    .testClass("Setup")
                    .testMethod("ClassLoader")
                    .error("Failed to load test directories: " + e.getMessage())
                    .build();
            return ExecutionReport.UnitTestPhase.builder()
                    .score(0.0)
                    .weight(weight)
                    .weightedScore(0.0)
                    .passedCount(0)
                    .totalCount(1)
                    .failures(List.of(fail))
                    .build();
        }

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader customClassLoader = new URLClassLoader(
                urls.toArray(new URL[0]),
                OopJUnit5TestRunner.class.getClassLoader())) {

            Thread.currentThread().setContextClassLoader(customClassLoader);

            LauncherDiscoveryRequestBuilder requestBuilder = LauncherDiscoveryRequestBuilder.request();
            for (String testClassName : testClassNames) {
                try {
                    Class<?> testClass = Class.forName(testClassName, true, customClassLoader);
                    requestBuilder.selectors(DiscoverySelectors.selectClass(testClass));
                } catch (ClassNotFoundException e) {
                    log.warn("Test class not found: {}", testClassName);
                    // Add selector by name directly so JUnit platform will report discovery failure
                    requestBuilder.selectors(DiscoverySelectors.selectClass(testClassName));
                }
            }

            int timeout = timeoutSecondsPerTest > 0 ? timeoutSecondsPerTest : 2;
            requestBuilder.configurationParameter("junit.jupiter.execution.timeout.default", timeout + " s");
            requestBuilder.configurationParameter("junit.jupiter.execution.timeout.mode", "enabled");
            requestBuilder.configurationParameter("junit.jupiter.execution.timeout.thread.mode", "separate_thread");

            LauncherDiscoveryRequest discoveryRequest = requestBuilder.build();
            Launcher launcher = LauncherFactory.create();

            HarnessTestExecutionListener listener = new HarnessTestExecutionListener();
            launcher.registerTestExecutionListeners(listener);
            launcher.execute(discoveryRequest);

            int total = listener.getTotalCount();
            int passed = listener.getPassedCount();
            double score = total > 0 ? Math.round(((double) passed / total) * 1000.0) / 10.0 : 0.0;
            double weightedScore = Math.round(score * weight * 10.0) / 10.0;

            return ExecutionReport.UnitTestPhase.builder()
                    .score(score)
                    .weight(weight)
                    .weightedScore(weightedScore)
                    .passedCount(passed)
                    .totalCount(total)
                    .failures(listener.getFailures())
                    .build();

        } catch (Throwable t) {
            log.error("Unhandled error during JUnit 5 test execution", t);
            ExecutionReport.UnitTestFailure fail = ExecutionReport.UnitTestFailure.builder()
                    .testClass("TestExecution")
                    .testMethod("run")
                    .error(t.getClass().getSimpleName() + ": " + t.getMessage())
                    .build();
            return ExecutionReport.UnitTestPhase.builder()
                    .score(0.0)
                    .weight(weight)
                    .weightedScore(0.0)
                    .passedCount(0)
                    .totalCount(1)
                    .failures(List.of(fail))
                    .build();
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static class HarnessTestExecutionListener implements TestExecutionListener {
        private int totalCount = 0;
        private int passedCount = 0;
        private final List<ExecutionReport.UnitTestFailure> failures = new ArrayList<>();

        public int getTotalCount() {
            return totalCount;
        }

        public int getPassedCount() {
            return passedCount;
        }

        public List<ExecutionReport.UnitTestFailure> getFailures() {
            return failures;
        }

        @Override
        public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
            if (!testIdentifier.isTest()) {
                return;
            }

            totalCount++;
            if (testExecutionResult.getStatus() == TestExecutionResult.Status.SUCCESSFUL) {
                passedCount++;
            } else {
                String testClass = "UnknownClass";
                String testMethod = testIdentifier.getDisplayName();

                if (testIdentifier.getSource().isPresent() && testIdentifier.getSource().get() instanceof MethodSource ms) {
                    testClass = ms.getClassName();
                    int dot = testClass.lastIndexOf('.');
                    if (dot >= 0) {
                        testClass = testClass.substring(dot + 1);
                    }
                    testMethod = ms.getMethodName();
                }

                String error = testExecutionResult.getThrowable()
                        .map(t -> {
                            String msg = t.getMessage();
                            return t.getClass().getSimpleName() + (msg != null ? ": " + msg : "");
                        })
                        .orElse("Test failed with status " + testExecutionResult.getStatus());

                failures.add(ExecutionReport.UnitTestFailure.builder()
                        .testClass(testClass)
                        .testMethod(testMethod)
                        .error(error)
                        .build());
            }
        }
    }
}
