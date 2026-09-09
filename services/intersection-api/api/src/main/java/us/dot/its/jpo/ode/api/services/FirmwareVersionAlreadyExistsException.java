package us.dot.its.jpo.ode.api.services;

public class FirmwareVersionAlreadyExistsException extends RuntimeException {
    public FirmwareVersionAlreadyExistsException(String message) {
        super(message);
    }
}
