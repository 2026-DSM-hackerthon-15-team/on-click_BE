package com.onclick.global.config;

import java.io.IOException;
import java.time.LocalTime;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public final class StrictLocalTimeDeserializer extends JsonDeserializer<LocalTime> {

    private static final Pattern HH_MM = Pattern.compile("(?:[01]\\d|2[0-3]):[0-5]\\d");

    @Override
    public LocalTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String value = parser.getValueAsString();
        if (value == null || !HH_MM.matcher(value).matches()) {
            throw context.weirdStringException(value, LocalTime.class, "expected time in HH:mm format");
        }
        return LocalTime.of(
                Integer.parseInt(value.substring(0, 2)),
                Integer.parseInt(value.substring(3, 5))
        );
    }
}
