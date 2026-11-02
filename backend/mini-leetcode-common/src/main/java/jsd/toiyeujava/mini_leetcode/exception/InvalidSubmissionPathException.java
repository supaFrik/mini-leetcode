package jsd.toiyeujava.mini_leetcode.exception;

/**
 * Thrown when an archive entry path violates security constraints:
 * Zip Slip (..), null bytes (\0), Windows drive letters, UNC paths,
 * absolute paths, or forbidden executable file extensions.
 */
public class InvalidSubmissionPathException extends IllegalArgumentException {

    public InvalidSubmissionPathException(String message) {
        super(message);
    }

    public InvalidSubmissionPathException(String message, Throwable cause) {
        super(message, cause);
    }
}
