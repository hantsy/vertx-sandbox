package com.example.demo

import com.fasterxml.jackson.core.type.TypeReference
import io.kotest.matchers.equals.shouldBeEqual
import io.vertx.core.Vertx
import io.vertx.core.json.jackson.DatabindCodec
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.awaitResult
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
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
    fun setUp() {
        vertx = Vertx.vertx()
        client = WebClient.create(vertx, WebClientOptions().setDefaultPort(8888))
        runBlocking(vertx.dispatcher()) {
            awaitResult<String> { vertx.deployVerticle(MainVerticle(), it) }
        }
    }

    @AfterEach
    fun tearDown() {
        vertx.close()
    }

    @Test
    fun testGetAllPosts() = runTest {
        val response = client.get("/posts").send().await()

        response.statusCode() shouldBeEqual 200
        println("response :${response.bodyAsString()}")
        val body = response.bodyAsString()
        val posts: List<Post> = DatabindCodec.mapper().readValue(body, object : TypeReference<List<Post>>() {})
        println("posts: ${posts}")
        //posts.size() shouldBeEqual 2
    }

    @Test
    fun testGetPostById_NotFound() = runTest(timeout = 500.milliseconds) {
        val id = UUID.randomUUID()
        val response = client.get("/posts/$id").send().await()
        response.statusCode() shouldBeEqual 404
    }
}
