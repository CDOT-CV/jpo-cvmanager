package us.dot.its.jpo.ode.api.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import us.dot.its.jpo.ode.api.models.storage.ObjectListRequest;
import us.dot.its.jpo.ode.api.models.storage.StorageObject;
import us.dot.its.jpo.ode.api.models.storage.StorageObjectPage;

class ObjectListingsTest {
    private final ObjectStorageService service = mock(ObjectStorageService.class);

    @Test
    void combinesEveryProviderPage() {
        var first = new StorageObject("ota/a.tar.sig", 1, null, "1", null);
        var second = new StorageObject("ota/b.tar.sig", 2, null, "2", null);
        when(service.listObjects(new ObjectListRequest("ota/", ObjectListings.PROVIDER_PAGE_SIZE, null)))
                .thenReturn(new StorageObjectPage("gcp", "bucket", List.of(first), "next"));
        when(service.listObjects(new ObjectListRequest("ota/", ObjectListings.PROVIDER_PAGE_SIZE, "next")))
                .thenReturn(new StorageObjectPage("gcp", "bucket", List.of(second), null));

        var page = ObjectListings.listAll(service, "ota/");

        assertThat(page).isEqualTo(new StorageObjectPage("gcp", "bucket", List.of(first, second), null));
        verify(service).listObjects(new ObjectListRequest("ota/", ObjectListings.PROVIDER_PAGE_SIZE, "next"));
    }

    @Test
    void failsWhenTheProviderRepeatsAPageToken() {
        when(service.listObjects(new ObjectListRequest(null, ObjectListings.PROVIDER_PAGE_SIZE, null)))
                .thenReturn(new StorageObjectPage("gcp", "bucket", List.of(), "loop"));
        when(service.listObjects(new ObjectListRequest(null, ObjectListings.PROVIDER_PAGE_SIZE, "loop")))
                .thenReturn(new StorageObjectPage("gcp", "bucket", List.of(), "loop"));

        assertThatThrownBy(() -> ObjectListings.listAll(service, null))
                .isInstanceOf(ObjectStorageUnavailableException.class)
                .hasMessageContaining("pagination");
    }
}
