package com.example.demo;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.rxjava3.core.RxHelper;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;
import io.vertx.rxjava3.ext.web.codec.BodyCodec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(VertxExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestMainVerticle {
    private static final Logger LOGGER = Logger.getLogger(TestMainVerticle.class.getName());
    WebClient client;

    @BeforeAll
    void setUp(Vertx vertx, VertxTestContext testContext) {
        RxHelper.deployVerticle(vertx, new MainVerticle())
            .subscribe(
                id ->  testContext.verify(() -> {
                    LOGGER.info("deployed: " + id);
                    this.client = WebClient.create(vertx);
                    testContext.completeNow();
                }),
                testContext::failNow
            );
    }

    @Test
    @Order(2)
    void testGetAll(VertxTestContext testContext) {
        client.get(8888, "localhost", "/posts")
            .as(BodyCodec.jsonArray())
            .rxSend()
            .subscribe(
                response -> testContext.verify(() -> {
                    assertThat(response.body().size()).isGreaterThan(0);
                    testContext.completeNow();
                }),
                testContext::failNow
            );
    }

    @Test
    @Order(3)
    void testCreatPost(VertxTestContext testContext) {
        client.post(8888, "localhost", "/posts")
            .putHeader("Content-Type", "application/json")
            .rxSendJson(new CreatePostCommand(
                "The quick brown fox jumps over the lazy dog",
                "body of the quick brown fox jumps over the lazy dog"
            ))
            .subscribe(
                response -> testContext.verify(() -> {
                    var statusCode = response.statusCode();
                    LOGGER.info("status code: " + statusCode);
                    assertThat(statusCode).isEqualTo(201);
                    assertThat(response.getHeader("Location")).isNotNull();
                    testContext.completeNow();
                }),
                testContext::failNow
            );
    }

    @Test
    @Order(1)
    void testHello(VertxTestContext testContext) {
        client.get(8888, "localhost", "/hello")
            .as(BodyCodec.string())
            .rxSend()
            .subscribe(
                response -> testContext.verify(() -> {
                    assertThat(response.body()).contains("Hello");
                    testContext.completeNow();
                }),
                testContext::failNow
            );
    }

}
