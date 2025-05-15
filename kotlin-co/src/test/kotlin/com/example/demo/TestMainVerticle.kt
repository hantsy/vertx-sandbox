package com.example.demo

import com.fasterxml.jackson.core.type.TypeReference
import io.kotest.matchers.equals.shouldBeEqual
import io.vertx.core.Vertx
import io.vertx.core.json.jackson.DatabindCodec
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.kotlin.coroutines.coAwait
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.time.Duration.Companion.milliseconds

// see: https://github.com/vietj/kotlin-conf-inter-reactive/blob/master/src/test/kotlin/com/julienviet/movierating/MovieRatingTest.kt
class TestMainVerticle {
    companion object {
        private val LOGGER = Logger.getLogger(TestMainVerticle::class.java.name)
    }

    lateinit var vertx: Vertx
    lateinit var client: WebClient

    @BeforeEach
    fun setUp() = runTest {
        vertx = Vertx.vertx()
        client = WebClient.create(vertx, WebClientOptions().setDefaultPort(8888))

        vertx.deployVerticle(MainVerticle())
            .onComplete {
                LOGGER.log(Level.INFO, "Deployed MainVerticle: $it")
            }
            .coAwait()
    }

    @AfterEach
    fun tearDown() {
        vertx.close()
    }

    @Test
    fun `get all posts`() = runTest {
        val response = client.get("/posts").send().coAwait()
        response.statusCode() shouldBeEqual 200
        LOGGER.info("response :${response.bodyAsString()}")
        val body = response.bodyAsString()
        val posts: List<Post> = DatabindCodec.mapper().readValue(body, object : TypeReference<List<Post>>() {})
        LOGGER.info("posts: ${posts}")
        //posts.size() shouldBeEqual 2
    }

    @Test
    fun `get post by none-existing id`() = runTest(timeout = 500.milliseconds) {
        val id = UUID.randomUUID()
        val response = client.get("/posts/$id").send().coAwait()
        response.statusCode() shouldBeEqual 404
    }
}
