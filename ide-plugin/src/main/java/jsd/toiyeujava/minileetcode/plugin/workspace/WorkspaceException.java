package jsd.toiyeujava.minileetcode.plugin.workspace;

/**
 * Exception thrown when workspace initialization fails due to filesystem errors,
 * network/API failures, Zip Slip security violations, or template resolution errors.
 */
public class WorkspaceException extends RuntimeException {

    public WorkspaceException(String message) {
        super(message);
    }

    public WorkspaceException(String message, Throwable cause) {
        super(message, cause);
    }
}
