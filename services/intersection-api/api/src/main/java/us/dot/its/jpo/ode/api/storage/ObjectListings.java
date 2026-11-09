package us.dot.its.jpo.ode.api.storage;

import java.util.ArrayList;
import java.util.HashSet;

import us.dot.its.jpo.ode.api.models.storage.ObjectListRequest;
import us.dot.its.jpo.ode.api.models.storage.StorageObject;
import us.dot.its.jpo.ode.api.models.storage.StorageObjectPage;

/** Reads complete provider listings across pages. */
public final class ObjectListings {
    static final int PROVIDER_PAGE_SIZE = 200;

    private ObjectListings() {
    }

    /**
     * Lists every object beneath a prefix. The returned page has no next-page
     * token. A provider that repeats a page token fails the listing rather than
     * looping forever.
     */
    public static StorageObjectPage listAll(ObjectStorageService service, String prefix) {
        var objects = new ArrayList<StorageObject>();
        var visitedTokens = new HashSet<String>();
        String pageToken = null;
        StorageObjectPage page;
        do {
            page = service.listObjects(new ObjectListRequest(prefix, PROVIDER_PAGE_SIZE, pageToken));
            objects.addAll(page.objects());

            pageToken = page.nextPageToken();
            if (pageToken != null && !pageToken.isBlank() && !visitedTokens.add(pageToken)) {
                throw new ObjectStorageUnavailableException("Object storage returned invalid pagination state");
            }
        } while (pageToken != null && !pageToken.isBlank());
        return new StorageObjectPage(page.provider(), page.container(), objects, null);
    }
}
