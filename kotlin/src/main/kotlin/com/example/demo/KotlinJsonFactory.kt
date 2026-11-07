package com.example.demo

import io.vertx.core.buffer.Buffer
import io.vertx.core.json.DecodeException
import io.vertx.core.json.EncodeException
import io.vertx.core.spi.JsonFactory
import io.vertx.core.spi.json.JsonCodec
import tools.jackson.databind.JacksonModule
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

/**
 * Replaces the Vert.x default Jackson 3 codec so the shared mapper registers
 * [KotlinModule]; Jackson 3 mappers are immutable, so modules must be wired at build time.
 */
class KotlinJsonFactory : JsonFactory {

    override fun order(): Int = 0

    override fun codec(): JsonCodec = CODEC

    companion object {
        // io.vertx.core.json.jackson.v3.VertxModule lives in a multi-release jar entry
        // invisible to the Kotlin compiler, so it is loaded reflectively at runtime.
        private fun vertxModule(): JacksonModule =
            Class.forName("io.vertx.core.json.jackson.v3.VertxModule")
                .getDeclaredConstructor()
                .newInstance() as JacksonModule

        @JvmField
        val MAPPER: ObjectMapper = JsonMapper.builder()
            .addModule(vertxModule())
            .addModule(KotlinModule.Builder().build())
            .build()

        private val CODEC = object : JsonCodec {
            override fun <T> fromString(json: String, clazz: Class<T>): T =
                try {
                    MAPPER.readValue(json, clazz)
                } catch (e: Exception) {
                    throw DecodeException(e.message, e)
                }

            override fun <T> fromBuffer(json: Buffer, clazz: Class<T>): T =
                try {
                    MAPPER.readValue(json.bytes, clazz)
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
