package us.dot.its.jpo.ode.api.services.firmware;

/**
 * Exception thrown when cloud storage operations fail
 */
public class CloudStorageException extends Exception {

    public CloudStorageException(String message) {
        super(message);
    }

    public CloudStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
