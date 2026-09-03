package us.dot.its.jpo.ode.api.storage;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Indexes all installed storage adapters by provider name. New uploads use the
 * configured active provider, while existing uploads can be routed back to the
 * provider recorded in PostgreSQL.
 */
@Component
public class ObjectStorageServiceRegistry {
    private final ObjectStorageProperties properties;
    private final Map<String, ObjectStorageService> services;

    public ObjectStorageServiceRegistry(ObjectStorageProperties properties, List<ObjectStorageService> services) {
        this.properties = properties;
        this.services = new HashMap<>();
        // Fail fast on duplicate names because routing would otherwise depend on
        // Spring bean iteration order
        for (ObjectStorageService service : services) {
            String provider = normalize(service.providerName());
            if (this.services.put(provider, service) != null) {
                throw new IllegalStateException("Multiple object storage services are registered for " + provider);
            }
        }
    }

    public ObjectStorageService getActiveService() {
        // Provider selection is deployment configuration, not firmware-domain logic
        return getService(properties.getProvider());
    }

    public ObjectStorageService getService(String provider) {
        // Case insensitive lookup allows persisted provider names and configuration
        // values to use different casing without changing provider identity
        ObjectStorageService service = services.get(normalize(provider));
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Object storage provider '" + provider + "' is not available");
        }
        return service;
    }

    private String normalize(String provider) {
        if (!StringUtils.hasText(provider)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Object storage provider is not configured");
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }
}
