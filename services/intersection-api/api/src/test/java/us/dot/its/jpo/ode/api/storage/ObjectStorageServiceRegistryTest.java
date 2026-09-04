package us.dot.its.jpo.ode.api.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ObjectStorageServiceRegistryTest {
    @Test
    void selectsConfiguredProviderAndRoutesStoredProvider() {
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setProvider("gcp");
        ObjectStorageService gcp = provider("gcp");
        ObjectStorageService another = provider("another-cloud");
        ObjectStorageServiceRegistry registry = new ObjectStorageServiceRegistry(
                properties, List.of(gcp, another));

        assertThat(registry.getActiveService()).isSameAs(gcp);
        assertThat(registry.getService("ANOTHER-CLOUD")).isSameAs(another);
    }

    @Test
    void reportsUnavailableConfiguredProvider() {
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setProvider("missing");
        ObjectStorageServiceRegistry registry = new ObjectStorageServiceRegistry(
                properties, List.of(provider("gcp")));

        assertThatThrownBy(registry::getActiveService)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private ObjectStorageService provider(String name) {
        ObjectStorageService service = mock(ObjectStorageService.class);
        when(service.providerName()).thenReturn(name);
        return service;
    }
}
