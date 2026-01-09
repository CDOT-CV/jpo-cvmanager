package us.dot.its.jpo.ode.api.models.emails.contents.serialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.Instant;

public class InstantFromMillisDeserializer extends JsonDeserializer<Instant> {
    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p == null) {
            return null;
        }
        return Instant.ofEpochMilli(p.getLongValue());
    }
}
