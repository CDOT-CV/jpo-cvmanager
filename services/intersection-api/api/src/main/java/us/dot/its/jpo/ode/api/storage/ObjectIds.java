package us.dot.its.jpo.ode.api.storage;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Opaque, URL-safe identifiers for listed storage objects. An identifier binds
 * an object name to the provider it was listed from.
 */
public final class ObjectIds {
    private ObjectIds() {
    }

    public record Identity(String provider, String objectName) {}

    /** An object identified together with the container it was recorded in. */
    public record ContainedIdentity(String provider, String container, String objectName) {}

    public static String encode(String provider, String objectName) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (provider + "\n" + objectName).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Encodes an identifier that also names the container, so objects with the same
     * name in different containers have different identifiers.
     */
    public static String encode(String provider, String container, String objectName) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (provider + "\n" + container + "\n" + objectName).getBytes(StandardCharsets.UTF_8));
    }

    /** Decodes an identifier, rejecting values that do not name a single object. */
    public static Identity decode(String objectId) {
        String identity = decodeText(objectId);
        int separator = identity.indexOf('\n');
        if (separator < 0) {
            throw invalid();
        }
        return new Identity(identity.substring(0, separator), objectName(identity.substring(separator + 1)));
    }

    /** Decodes an identifier made with {@link #encode(String, String, String)}. */
    public static ContainedIdentity decodeContained(String objectId) {
        String identity = decodeText(objectId);
        int first = identity.indexOf('\n');
        int second = first < 0 ? -1 : identity.indexOf('\n', first + 1);
        if (second < 0) {
            throw invalid();
        }
        String container = identity.substring(first + 1, second);
        if (container.isBlank()) {
            throw invalid();
        }
        return new ContainedIdentity(identity.substring(0, first), container,
                objectName(identity.substring(second + 1)));
    }

    private static String decodeText(String objectId) {
        try {
            return new String(Base64.getUrlDecoder().decode(objectId), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw invalid();
        }
    }

    /** Rejects names that do not identify a single object, including any further separator. */
    private static String objectName(String name) {
        if (name.isBlank() || name.endsWith("/") || name.chars().anyMatch(Character::isISOControl)) {
            throw invalid();
        }
        return name;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid firmware object ID");
    }
}
