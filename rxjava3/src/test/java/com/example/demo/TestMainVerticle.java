package com.example.demo;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.rxjava3.ext.web.client.WebClient;
import io.vertx.rxjava3.ext.web.codec.BodyCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
public class TestMainVerticle {
    private static final Logger LOGGER = Logger.getLogger(TestMainVerticle.class.getName());
    WebClient client;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        vertx.deployVerticle(new MainVerticle())
            .onComplete(testContext.succeeding(id -> {
                LOGGER.info("deployed: " + id);
                this.client = WebClient.create(io.vertx.rxjava3.core.Vertx.newInstance(vertx));
                testContext.completeNow();
            }));
    }

    @Test
    void testGetAll(VertxTestContext testContext) {
        client.get(8888, "localhost", "/posts")
            .as(BodyCodec.jsonArray())
            .rxSend()
            .subscribe(
                response -> testContext.verify(() -> {
                    assertThat(response.body().size()).isEqualTo(2);
                    testContext.completeNow();
                }),
                testContext::failNow
            );
    }

    @Test
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
