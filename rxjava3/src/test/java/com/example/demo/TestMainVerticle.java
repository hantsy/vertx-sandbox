package com.example.demo;

import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.rxjava3.core.RxHelper;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
public class TestMainVerticle {
    WebClient client;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {

        // see: https://github.com/vert-x3/vertx-rx/blob/master/rx-junit5-providers/vertx-junit5-rx-java3/src/test/java/io/vertx/junit5/rxjava3/RxJava3Test.java
        RxHelper
            .deployVerticle(vertx, new MainVerticle())
            .subscribe(
                message -> testContext.verify(() -> {
                    log.info("deployed: {}", message);
                    testContext.completeNow();
                }),
                testContext::failNow);

        // build RxJava3 WebClient
        var options = new WebClientOptions()
            .setDefaultHost("localhost")
            .setDefaultPort(8888);
        this.client = WebClient.create(vertx, options);
    }

    @Test
    void testGetAll(Vertx vertx, VertxTestContext testContext) {
        client.get("/posts")
            .rxSend()
            .subscribe(
                response -> {
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.bodyAsJsonArray().size()).isEqualTo(2);

                    testContext.completeNow();
                },
                testContext::failNow
            );
    }

    @Test
    void testPostValidation(Vertx vertx, VertxTestContext testContext) {
        client.post("/posts")
            .rxSendJson(CreatePostCommand.of("test", "t"))
            .subscribe(
                response -> {
                    var statusCode = response.statusCode();
                    var body = response.bodyAsString();
                    log.info("status code: {}, body: {}", statusCode, body);
                    assertThat(statusCode).isEqualTo(400);
                    assertThat(body).isEqualTo("validation failed.");
                    testContext.completeNow();
                },
                testContext::failNow
            );
    }

    @Test
    void testPostValidationOK(Vertx vertx, VertxTestContext testContext) {
        client.post("/posts")
            .rxSendJson(
                CreatePostCommand.of("The quick brown fox jumps over the lazy dog",
                    "body of `the quick brown fox jumps over the lazy dog`")
            )
            .subscribe(
                response -> {
                    var statusCode = response.statusCode();
                    var body = response.bodyAsString();
                    log.info("status code: {}, body: {}", statusCode, body);
                    assertThat(statusCode).isEqualTo(201);
                    assertThat(response.getHeader("Location")).isNotNull();
                    testContext.completeNow();
                },
                testContext::failNow
            );
    }

}
