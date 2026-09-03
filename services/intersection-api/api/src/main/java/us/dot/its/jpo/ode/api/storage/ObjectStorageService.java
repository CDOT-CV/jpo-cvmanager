package us.dot.its.jpo.ode.api.storage;

import java.util.Optional;

import us.dot.its.jpo.ode.api.models.storage.ObjectStorageLocation;
import us.dot.its.jpo.ode.api.models.storage.ObjectUploadRequest;
import us.dot.its.jpo.ode.api.models.storage.SignedUploadUrl;
import us.dot.its.jpo.ode.api.models.storage.StoredObjectMetadata;

/** Cloud-provider-neutral operations for direct object-storage uploads. */
public interface ObjectStorageService {
    SignedUploadUrl createSignedUploadUrl(ObjectUploadRequest request);

    Optional<StoredObjectMetadata> getObjectMetadata(ObjectStorageLocation location, String checksumAlgorithm);

    String providerName();
}
