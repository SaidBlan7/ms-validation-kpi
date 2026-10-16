package com.gbconnected.kpivalidation.functions;

import com.google.gson.*;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDate;

final class JsonCodec {
    static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LocalDate.class, new JsonSerializer<LocalDate>() {
                public JsonElement serialize(LocalDate src, Type type, JsonSerializationContext context) {
                    return new JsonPrimitive(src.toString());
                }
            })
            .registerTypeAdapter(Instant.class, new JsonSerializer<Instant>() {
                public JsonElement serialize(Instant src, Type type, JsonSerializationContext context) {
                    return new JsonPrimitive(src.toString());
                }
            })
            .create();

    private JsonCodec() {
    }
}
