package us.dot.its.jpo.ode.api.services.firmware;

/**
 * Exception thrown when firmware service operations fail
 */
public class FirmwareServiceException extends Exception {

    public FirmwareServiceException(String message) {
        super(message);
    }

    public FirmwareServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
