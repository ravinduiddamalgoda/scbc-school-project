package com.scbck.json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

/**
 * Reads a "data:image/..." URL string from JSON back into the UTF-8 byte array
 * the entity persists. Counterpart of {@link DataUrlSerializer}.
 */
public class DataUrlDeserializer extends JsonDeserializer<byte[]> {

    @Override
    public byte[] deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String value = parser.getValueAsString();
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
