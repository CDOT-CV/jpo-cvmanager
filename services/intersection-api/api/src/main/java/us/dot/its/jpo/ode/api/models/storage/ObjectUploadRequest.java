package us.dot.its.jpo.ode.api.models.storage;

/** Provider-neutral input for signing one direct object upload. */
public record ObjectUploadRequest(
        String objectName,
        long contentLength,
        String contentType,
        ObjectChecksum checksum) {
}
