package com.example.demo

import io.kotest.matchers.equals.shouldBeEqual
import io.vertx.core.Vertx

import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.ext.web.codec.BodyCodec
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.awaitResult
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

class TestMainVerticle {

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
    fun testGetAllPosts() = runBlocking(vertx.dispatcher()) {
        val response = client.get("/posts").`as`(BodyCodec.jsonArray()).send().await()

        response.statusCode() shouldBeEqual 200
        val body = response.bodyAsJsonArray().map { (it as JsonObject).mapTo(Post::class.java) }
        body.size shouldBeEqual 2
    }

    @Test
    fun testGetPostById_NotFound() = runTest(UnconfinedTestDispatcher()) {
        val id = UUID.randomUUID()
        val response = client.get("/posts/$id").`as`(BodyCodec.jsonObject()).send().await()
        response.statusCode() shouldBeEqual 404
    }
}
