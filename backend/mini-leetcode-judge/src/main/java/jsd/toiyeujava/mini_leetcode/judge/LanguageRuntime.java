package jsd.toiyeujava.mini_leetcode.judge;

import jsd.toiyeujava.mini_leetcode.entity.SubmissionLanguage;

import java.util.List;
import java.util.Optional;

public enum LanguageRuntime {

    PYTHON(
            "python:3.12-slim",
            "solution.py",
            List.of(),
            List.of("python", "/workspace/solution.py")),

    JAVA(
            "eclipse-temurin:17-jdk",
            "Solution.java",
            List.of("javac", "-encoding", "UTF-8", "/workspace/Solution.java"),
            List.of("java", "-Xmx256m", "-cp", "/workspace", "Solution"));

    private final String image;
    private final String sourceFileName;
    private final List<String> compileCommand;
    private final List<String> runCommand;

    LanguageRuntime(String image, String sourceFileName,
                    List<String> compileCommand, List<String> runCommand) {
        this.image = image;
        this.sourceFileName = sourceFileName;
        this.compileCommand = compileCommand;
        this.runCommand = runCommand;
    }

    public String image() {
        return image;
    }

    public String sourceFileName() {
        return sourceFileName;
    }

    public List<String> compileCommand() {
        return compileCommand;
    }

    public List<String> runCommand() {
        return runCommand;
    }

    public boolean needsCompile() {
        return !compileCommand.isEmpty();
    }

    public static Optional<LanguageRuntime> from(SubmissionLanguage language) {
        if (language == null) {
            return Optional.empty();
        }
        return switch (language) {
            case PYTHON -> Optional.of(PYTHON);
            case JAVA -> Optional.of(JAVA);
            default -> Optional.empty();
        };
    }
}