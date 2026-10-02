package us.dot.its.jpo.ode.api.storage;

import java.util.Optional;

import us.dot.its.jpo.ode.api.models.storage.ObjectListRequest;
import us.dot.its.jpo.ode.api.models.storage.ObjectStorageLocation;
import us.dot.its.jpo.ode.api.models.storage.ObjectUploadRequest;
import us.dot.its.jpo.ode.api.models.storage.SignedUploadUrl;
import us.dot.its.jpo.ode.api.models.storage.StorageObjectPage;
import us.dot.its.jpo.ode.api.models.storage.StoredObjectMetadata;

/** Cloud-provider-neutral operations for direct object-storage uploads. */
public interface ObjectStorageService {
    /** Resolves an object within the configured container; clients never choose the container. */
    ObjectStorageLocation getLocation(String objectName);

    /** Deletes the live object only if its version matches; an absent object is already deleted. */
    void deleteObject(ObjectStorageLocation location, String providerObjectVersion);

    /** Lists one bounded page beneath a provider-neutral object-name prefix. */
    StorageObjectPage listObjects(ObjectListRequest request);

    /** Checks the active container without creating or replacing an object. */
    boolean objectExists(String objectName);

    SignedUploadUrl createSignedUploadUrl(ObjectUploadRequest request);

    Optional<StoredObjectMetadata> getObjectMetadata(ObjectStorageLocation location, String checksumAlgorithm);

    String providerName();

    class ObjectStorageConflictException extends RuntimeException {
        public ObjectStorageConflictException(String message) {
            super(message);
        }
    }
}
