package us.dot.its.jpo.ode.api.storage;

/** The stored object no longer matches the version selected for an operation. */
public class ObjectStorageConflictException extends RuntimeException {
    public ObjectStorageConflictException(String message) {
        super(message);
    }
}
