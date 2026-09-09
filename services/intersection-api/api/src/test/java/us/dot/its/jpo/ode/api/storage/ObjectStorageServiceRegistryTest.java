package us.dot.its.jpo.ode.api.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

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
                .isInstanceOf(ObjectStorageUnavailableException.class)
                .hasMessage("Object storage provider 'missing' is not available");
    }

    @Test
    void reportsUnconfiguredProvider() {
        ObjectStorageProperties properties = new ObjectStorageProperties();
        ObjectStorageServiceRegistry registry = new ObjectStorageServiceRegistry(
                properties, List.of(provider("gcp")));

        properties.setProvider(null);
        assertThatThrownBy(registry::getActiveService)
                .isInstanceOf(ObjectStorageUnavailableException.class)
                .hasMessage("Object storage provider is not configured");
        properties.setProvider(" ");
        assertThatThrownBy(registry::getActiveService)
                .isInstanceOf(ObjectStorageUnavailableException.class)
                .hasMessage("Object storage provider is not configured");
    }

    private ObjectStorageService provider(String name) {
        ObjectStorageService service = mock(ObjectStorageService.class);
        when(service.providerName()).thenReturn(name);
        return service;
    }
}
