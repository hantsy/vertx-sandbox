package com.example.demo

import io.kotest.matchers.equals.shouldBeEqual
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.ext.web.codec.BodyCodec
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TestMainVerticle {

    lateinit var vertx: Vertx
    lateinit var client: WebClient

    @BeforeEach
    fun setUp() = runTest {
        vertx = Vertx.vertx()
        client = WebClient.create(vertx, WebClientOptions().setDefaultPort(8888))
        vertx.deployVerticle(MainVerticle()).await()
    }

    @AfterEach
    fun tearDown() {
        vertx.close()
    }

    @Test
    fun testGetAllPosts() = runTest {
        val response = client.get("/posts").`as`(BodyCodec.jsonArray()).send().await()

        response.statusCode() shouldBeEqual 200
        val body = response.bodyAsJsonArray().map { (it as JsonObject).mapTo(Post::class.java) }
        body.size shouldBeEqual 2
    }
}
