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
        String[] identity = split(objectId, 2);
        return new Identity(identity[0], identity[1]);
    }

    /** Decodes an identifier made with {@link #encode(String, String, String)}. */
    public static ContainedIdentity decodeContained(String objectId) {
        String[] identity = split(objectId, 3);
        if (identity[1].isBlank()) {
            throw new IllegalArgumentException("Invalid firmware object ID");
        }
        return new ContainedIdentity(identity[0], identity[1], identity[2]);
    }

    private static String[] split(String objectId, int parts) {
        String[] identity;
        try {
            identity = new String(Base64.getUrlDecoder().decode(objectId), StandardCharsets.UTF_8)
                    .split("\n", parts);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid firmware object ID");
        }
        String objectName = identity[identity.length - 1];
        if (identity.length != parts || objectName.isBlank() || objectName.endsWith("/")
                || objectName.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid firmware object ID");
        }
        return identity;
    }
}
