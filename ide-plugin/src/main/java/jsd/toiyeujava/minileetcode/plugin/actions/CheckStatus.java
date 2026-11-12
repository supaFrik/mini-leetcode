package jsd.toiyeujava.minileetcode.plugin.actions;

/**
 * Status outcome of a stage validation or check operation.
 */
public enum CheckStatus {
    /** All local project files and metadata are intact; ready for submission. */
    SUCCESS,
    /** Project has missing auxiliary files or warnings that do not block checks. */
    WARNING,
    /** Fatal error such as corrupted manifest, unparseable YAML, or missing source directory. */
    ERROR,
    /** Directory is not a valid Mini LeetCode project. */
    NOT_MLC_PROJECT
}
