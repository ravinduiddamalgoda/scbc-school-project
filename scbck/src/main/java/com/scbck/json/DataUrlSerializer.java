package com.scbck.json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * Writes a photo column (stored as the UTF-8 bytes of a "data:image/..." URL)
 * as a plain JSON string.
 *
 * Jackson's default byte[] handling base64-encodes the bytes, which forced the
 * old jQuery client to call atob() on data that was already a data URL. Doing
 * the decode here keeps the wire format honest: the client receives exactly
 * what it can assign to an <img src>.
 */
public class DataUrlSerializer extends JsonSerializer<byte[]> {

    @Override
    public void serialize(byte[] value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null || value.length == 0) {
            gen.writeNull();
            return;
        }
        gen.writeString(new String(value, StandardCharsets.UTF_8));
    }
}
