package us.dot.its.jpo.ode.api.models.storage;

/** Provider-neutral representation of a checksum supplied for an object. */
public record ObjectChecksum(String algorithm, String value) {
}
