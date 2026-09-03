package us.dot.its.jpo.ode.api.storage;

import us.dot.its.jpo.ode.api.models.storage.SignedUploadUrl;
import us.dot.its.jpo.ode.api.models.storage.SignedUploadUrlRequest;

/** Cloud-provider-neutral operations for direct object-storage uploads. */
public interface ObjectStorageService {
    SignedUploadUrl createSignedUploadUrl(SignedUploadUrlRequest request);
}
