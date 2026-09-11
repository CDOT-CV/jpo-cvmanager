package us.dot.its.jpo.ode.api.models.storage;

/** Identifies an object without imposing provider-specific naming terminology. */
public record ObjectStorageLocation(
        String provider,
        String container,
        String objectName) {
}
