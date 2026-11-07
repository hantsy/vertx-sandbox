package com.example.demo

import com.fasterxml.jackson.annotation.JsonInclude
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.DecodeException
import io.vertx.core.json.EncodeException
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.spi.JsonFactory
import io.vertx.core.spi.json.JsonCodec
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

/**
 * Replaces the Vert.x default Jackson 3 codec so the shared mapper registers
 * [KotlinModule]; Jackson 3 mappers are immutable, so customization goes through
 * rebuild() on the built-in DatabindCodec mapper instead of mutating it.
 */
class KotlinJsonFactory : JsonFactory {

    override fun order(): Int = 0

    override fun codec(): JsonCodec = CODEC

    companion object {
        // io.vertx.core.json.jackson.v3.DatabindCodec lives in a multi-release jar entry
        // invisible to the Kotlin compiler, so it is loaded reflectively at runtime.
        private fun builtInMapper(): JsonMapper =
            Class.forName("io.vertx.core.json.jackson.v3.DatabindCodec")
                .getMethod("mapper")
                .invoke(null) as JsonMapper

        @JvmField
        val MAPPER: ObjectMapper = builtInMapper()
            .rebuild()
            .addModule(KotlinModule.Builder().build())
            // Jackson 3 replacement for the former DatabindCodec.mapper() customization:
            // since mappers are immutable, inclusion is configured on the rebuild() builder.
            .changeDefaultPropertyInclusion { incl ->
                incl.withValueInclusion(JsonInclude.Include.NON_EMPTY)
            }
            .build()

        // Matches the default DatabindCodec: when decoding to Object (e.g. BodyHandler's
        // asJsonObject path), Jackson's Map/List results must be adapted to JsonObject/JsonArray.
        @Suppress("UNCHECKED_CAST")
        private fun <T> adapt(value: T, clazz: Class<T>): T =
            if (clazz == Object::class.java) {
                when (value) {
                    is Map<*, *> -> JsonObject(value as Map<String, Any?>) as T
                    is List<*> -> JsonArray(value) as T
                    else -> value
                }
            } else {
                value
            }

        private val CODEC = object : JsonCodec {
            override fun <T> fromString(json: String, clazz: Class<T>): T =
                try {
                    adapt(MAPPER.readValue(json, clazz), clazz)
                } catch (e: Exception) {
                    throw DecodeException(e.message, e)
                }

            override fun <T> fromBuffer(json: Buffer, clazz: Class<T>): T =
                try {
                    adapt(MAPPER.readValue(json.bytes, clazz), clazz)
                } catch (e: Exception) {
                    throw DecodeException(e.message, e)
                }

            override fun <T> fromValue(json: Any?, toValueType: Class<T>): T =
                MAPPER.convertValue(json, toValueType)

            override fun toString(obj: Any?, pretty: Boolean): String =
                try {
                    if (pretty) {
                        MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj)
                    } else {
                        MAPPER.writeValueAsString(obj)
                    }
                } catch (e: Exception) {
                    throw EncodeException(e.message, e)
                }

            override fun toBuffer(obj: Any?, pretty: Boolean): Buffer =
                try {
                    val bytes = if (pretty) {
                        MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(obj)
                    } else {
                        MAPPER.writeValueAsBytes(obj)
                    }
                    Buffer.buffer(bytes)
                } catch (e: Exception) {
                    throw EncodeException(e.message, e)
                }
        }
    }
}
