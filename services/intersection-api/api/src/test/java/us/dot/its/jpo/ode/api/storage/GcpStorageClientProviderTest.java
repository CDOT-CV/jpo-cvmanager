package us.dot.its.jpo.ode.api.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

class GcpStorageClientProviderTest {
    @Test
    void lazilyInitializesAndCachesScopedCredentialsAndStorage() throws Exception {
        GoogleCredentials adc = mock(GoogleCredentials.class);
        GoogleCredentials scoped = mock(GoogleCredentials.class);
        StorageOptions.Builder builder = mock(StorageOptions.Builder.class);
        StorageOptions options = mock(StorageOptions.class);
        Storage storage = mock(Storage.class);
        when(adc.createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"))).thenReturn(scoped);
        when(builder.setCredentials(scoped)).thenReturn(builder);
        when(builder.build()).thenReturn(options);
        when(options.getService()).thenReturn(storage);

        // Replace the SDK construction boundary; exercise the provider's real initialization and cache.
        try (MockedStatic<GoogleCredentials> credentialsFactory = mockStatic(GoogleCredentials.class);
                MockedStatic<StorageOptions> storageFactory = mockStatic(StorageOptions.class)) {
            credentialsFactory.when(GoogleCredentials::getApplicationDefault).thenReturn(adc);
            storageFactory.when(StorageOptions::newBuilder).thenReturn(builder);
            GcpStorageClientProvider provider = new GcpStorageClientProvider();
            credentialsFactory.verifyNoInteractions();
            storageFactory.verifyNoInteractions();

            assertThat(provider.getStorage()).isSameAs(storage);
            assertThat(provider.getStorage()).isSameAs(storage);
            assertThat(provider.getCredentials()).isSameAs(scoped);
            assertThat(provider.getCredentials()).isSameAs(scoped);
            credentialsFactory.verify(GoogleCredentials::getApplicationDefault, times(1));
            storageFactory.verify(StorageOptions::newBuilder, times(1));
            verify(builder).setCredentials(scoped);
        }
    }

    @Test
    void retriesCredentialInitializationAfterAnIoFailure() throws Exception {
        GoogleCredentials credentials = mock(GoogleCredentials.class);
        when(credentials.createScoped(List.of("https://www.googleapis.com/auth/cloud-platform")))
                .thenReturn(credentials);
        IOException failure = new IOException("ADC unavailable");
        try (MockedStatic<GoogleCredentials> factory = mockStatic(GoogleCredentials.class)) {
            factory.when(GoogleCredentials::getApplicationDefault).thenThrow(failure).thenReturn(credentials);
            GcpStorageClientProvider provider = new GcpStorageClientProvider();
            assertThatThrownBy(provider::getCredentials).isSameAs(failure);
            assertThat(provider.getCredentials()).isSameAs(credentials);
            factory.verify(GoogleCredentials::getApplicationDefault, times(2));
        }
    }
}
