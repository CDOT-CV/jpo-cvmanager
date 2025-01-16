package us.dot.its.jpo.ode.api.serialization;

import java.io.IOException;
import java.util.HexFormat;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

public class HexSerializer extends StdSerializer<byte[]> {
    public HexSerializer() {
        super(byte[].class);
    }

    private final static HexFormat hexFormat = HexFormat.of();
    
    @Override
    public void serialize(byte[] bytes, JsonGenerator generator, SerializerProvider provider) 
            throws IOException {
       generator.writeString(hexFormat.formatHex(bytes));
    }
}
