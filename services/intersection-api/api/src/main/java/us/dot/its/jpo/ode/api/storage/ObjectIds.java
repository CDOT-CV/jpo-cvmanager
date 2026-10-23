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

    public static String encode(String provider, String objectName) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (provider + "\n" + objectName).getBytes(StandardCharsets.UTF_8));
    }

    /** Decodes an identifier, rejecting values that do not name a single object. */
    public static Identity decode(String objectId) {
        String[] identity;
        try {
            identity = new String(Base64.getUrlDecoder().decode(objectId), StandardCharsets.UTF_8).split("\n", 2);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid firmware object ID");
        }
        if (identity.length != 2 || identity[1].isBlank() || identity[1].endsWith("/")
                || identity[1].chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid firmware object ID");
        }
        return new Identity(identity[0], identity[1]);
    }
}
