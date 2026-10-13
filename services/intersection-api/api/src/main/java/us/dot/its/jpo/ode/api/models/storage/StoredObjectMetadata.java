package us.dot.its.jpo.ode.api.models.storage;

/** Provider-neutral metadata used to verify a completed direct upload. */
public record StoredObjectMetadata(
        long contentLength,
        ObjectChecksum checksum,
        String providerObjectVersion) {
}
