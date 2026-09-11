package us.dot.its.jpo.ode.api.models.storage;

import java.time.Instant;

public record StorageObject(String objectName, long contentLength, Instant updatedAt,
        String providerObjectVersion, ObjectChecksum checksum) {}
