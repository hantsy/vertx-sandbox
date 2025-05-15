package com.example.demo

import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpClientResponse
import io.vertx.core.http.HttpMethod
import io.vertx.junit5.Timeout
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger


@ExtendWith(VertxExtension::class)
class TestMainVerticle {
    companion object {
        private val LOGGER = Logger.getLogger(TestMainVerticle::class.java.name)
    }

    lateinit var client: HttpClient

    @BeforeEach
    fun setup(vertx: Vertx, testContext: VertxTestContext) {
        vertx.deployVerticle(MainVerticle())
            .onComplete(
                testContext.succeeding { id: String? ->
                    LOGGER.log(Level.INFO, "deployed:{0}", arrayOf(id))
                    val options = HttpClientOptions()
                        .setDefaultPort(8888)
                    this.client = vertx.createHttpClient(options)
                    testContext.completeNow()
                }
            )
    }

    @AfterEach
    @DisplayName("Check that the verticle is still there")
    fun lastChecks(vertx: Vertx) {
        assertThat(vertx.deploymentIDs())
            .isNotEmpty()
            .hasSize(1)
    }

    // Repeat this test 3 times
    @RepeatedTest(3)
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Check the HTTP response...")
    fun testHello(vertx: Vertx, testContext: VertxTestContext) {
        client.request(HttpMethod.GET, "/hello")
            .flatMap { it.send() }
            .flatMap { it.body() }
            .onComplete(
                testContext.succeeding { buffer: Buffer ->
                    testContext.verify {
                        assertThat(buffer.toString()).contains("Hello")
                        testContext.completeNow()
                    }
                }
            )
    }

    @Test
    fun `get all posts`(vertx: Vertx, testContext: VertxTestContext) {
        client.request(HttpMethod.GET, "/posts")
            .flatMap { it.send() }
            .flatMap { it.body() }
            .onComplete(
                testContext.succeeding { buffer: Buffer ->
                    testContext.verify {
                        assertThat(buffer.toJsonArray().size()).isEqualTo(2)
                        testContext.completeNow()
                    }
                }
            )
    }

    @Test
    fun `get post by non-existing id`(vertx: Vertx, testContext: VertxTestContext) {
        val postByIdUrl = "/posts/" + UUID.randomUUID()
        client.request(HttpMethod.GET, postByIdUrl)
            .flatMap { it.send() }
            .onComplete(
                testContext.succeeding { response: HttpClientResponse ->
                    testContext.verify {
                        assertThat(response.statusCode()).isEqualTo(404)
                        testContext.completeNow()
                    }
                }
            )
    }
}
