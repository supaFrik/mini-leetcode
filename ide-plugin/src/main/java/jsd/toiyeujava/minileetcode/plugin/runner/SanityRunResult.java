package jsd.toiyeujava.minileetcode.plugin.runner;

/**
 * Immutable outcome of a local sanity execution of main().
 *
 * @param exitCode process exit code (0 = success, non-zero = failure/error, -1 = timeout)
 * @param stdout   captured standard output stream content
 * @param stderr   captured standard error stream content (e.g. compilation errors, exceptions)
 * @param timedOut true if the execution was killed due to exceeding the timeout limit
 */
public record SanityRunResult(
        int exitCode,
        String stdout,
        String stderr,
        boolean timedOut
) {
    public SanityRunResult {
        stdout = (stdout == null) ? "" : stdout;
        stderr = (stderr == null) ? "" : stderr;
    }

    public boolean isSuccess() {
        return !timedOut && exitCode == 0;
    }

    public static SanityRunResult timeout(String stdout, String stderr) {
        return new SanityRunResult(-1, stdout, stderr, true);
    }

    public static SanityRunResult error(int exitCode, String stderr) {
        return new SanityRunResult(exitCode, "", stderr, false);
    }

    public static SanityRunResult success(String stdout) {
        return new SanityRunResult(0, stdout, "", false);
    }
}
