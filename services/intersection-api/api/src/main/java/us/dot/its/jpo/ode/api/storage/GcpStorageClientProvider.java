package us.dot.its.jpo.ode.api.storage;

import java.io.IOException;
import java.util.List;

import org.springframework.stereotype.Component;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

/**
 * Lazily resolves Application Default Credentials so application startup and
 * unit tests do not require access to Google Cloud.
 */
@Component
public class GcpStorageClientProvider {
    private volatile GoogleCredentials credentials;
    private volatile Storage storage;

    public GoogleCredentials getCredentials() throws IOException {
        if (credentials == null) {
            synchronized (this) {
                if (credentials == null) {
                    credentials = GoogleCredentials.getApplicationDefault()
                            .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));
                }
            }
        }
        return credentials;
    }

    public Storage getStorage() throws IOException {
        if (storage == null) {
            synchronized (this) {
                if (storage == null) {
                    storage = StorageOptions.newBuilder()
                            .setCredentials(getCredentials())
                            .build()
                            .getService();
                }
            }
        }
        return storage;
    }
}
