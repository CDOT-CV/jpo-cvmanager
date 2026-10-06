package us.dot.its.jpo.ode.api.accessors.counts;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses {@code message} query parameters that may be repeated, comma-separated, or both.
 */
public final class MessageTypeParams {

    private MessageTypeParams() {
    }

    public static List<String> parse(String message) {
        return parse(message == null ? List.of() : List.of(message));
    }

    public static List<String> parse(List<String> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        Set<String> types = new LinkedHashSet<>();
        for (String message : messages) {
            if (message == null || message.isBlank()) {
                continue;
            }
            for (String part : message.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    types.add(trimmed.toUpperCase());
                }
            }
        }
        return new ArrayList<>(types);
    }
}
