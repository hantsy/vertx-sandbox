package com.example.demo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.EncodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.v3.DatabindCodec;
import io.vertx.core.spi.JsonFactory;
import io.vertx.core.spi.json.JsonCodec;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Replaces the Vert.x default Jackson 3 codec to demonstrate mapper customization.
 * Jackson 3 mappers are immutable, so the old {@code DatabindCodec.mapper().configure(...)}
 * pattern becomes {@code rebuild()} on the built-in mapper: keep the default configuration
 * (including VertxModule) and only change the property inclusion.
 */
public class CustomJsonFactory implements JsonFactory {

    public static final ObjectMapper MAPPER = DatabindCodec.mapper()
            .rebuild()
            .changeDefaultPropertyInclusion(incl ->
                    incl.withValueInclusion(JsonInclude.Include.NON_NULL))
            .build();

    private static final JsonCodec CODEC = new JsonCodec() {

        // Matches the default DatabindCodec: when decoding to Object (e.g. JsonObject.mapFrom
        // or BodyHandler's asJsonObject path), Jackson's Map/List results must be adapted.
        @SuppressWarnings("unchecked")
        private static <T> T adapt(Object value, Class<T> clazz) {
            if (clazz == Object.class) {
                if (value instanceof Map<?, ?> map) {
                    return (T) new JsonObject((Map<String, Object>) map);
                }
                if (value instanceof List<?> list) {
                    return (T) new JsonArray(list);
                }
            }
            return (T) value;
        }

        @Override
        public <T> T fromString(String json, Class<T> clazz) {
            try {
                return adapt(MAPPER.readValue(json, clazz), clazz);
            } catch (Exception e) {
                throw new DecodeException(e.getMessage(), e);
            }
        }

        @Override
        public <T> T fromBuffer(Buffer json, Class<T> clazz) {
            try {
                return adapt(MAPPER.readValue(json.getBytes(), clazz), clazz);
            } catch (Exception e) {
                throw new DecodeException(e.getMessage(), e);
            }
        }

        @Override
        public <T> T fromValue(Object json, Class<T> toValueType) {
            return MAPPER.convertValue(json, toValueType);
        }

        @Override
        public String toString(Object object, boolean pretty) {
            try {
                return pretty
                        ? MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(object)
                        : MAPPER.writeValueAsString(object);
            } catch (Exception e) {
                throw new EncodeException(e.getMessage(), e);
            }
        }

        @Override
        public Buffer toBuffer(Object object, boolean pretty) {
            try {
                byte[] bytes = pretty
                        ? MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(object)
                        : MAPPER.writeValueAsBytes(object);
                return Buffer.buffer(bytes);
            } catch (Exception e) {
                throw new EncodeException(e.getMessage(), e);
            }
        }
    };

    @Override
    public int order() {
        return 0;
    }

    @Override
    public JsonCodec codec() {
        return CODEC;
    }
}
