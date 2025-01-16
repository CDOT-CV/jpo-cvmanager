package us.dot.its.jpo.ode.api.serialization;

import java.io.IOException;
import java.util.HexFormat;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

public class HexDeserializer extends StdDeserializer<byte[]> {

    public HexDeserializer() {
        super(byte[].class);
    }

    private final static HexFormat hexFormat = HexFormat.of();

    @Override
    public byte[] deserialize(JsonParser p, DeserializationContext ctxt) 
            throws IOException, JacksonException {
        String hex = p.getText();
        return hexFormat.parseHex(hex);
    }
    
}
