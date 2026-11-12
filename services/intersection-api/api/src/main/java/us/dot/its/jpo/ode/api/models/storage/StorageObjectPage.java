package us.dot.its.jpo.ode.api.models.storage;

import java.util.List;

public record StorageObjectPage(String provider, String container, List<StorageObject> objects,
        String nextPageToken) {}
