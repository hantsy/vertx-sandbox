package com.example.demo;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import io.vertx.rxjava3.ext.web.client.WebClient;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
public class TestMainVerticle {
    WebClient client;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        vertx.deployVerticle(new MainVerticle(), testContext.succeeding(id -> testContext.completeNow()));

        // build RxJava3 WebClient
        var rxVertx = new io.vertx.rxjava3.core.Vertx(vertx);
        var options = new WebClientOptions()
            .setDefaultHost("localhost")
            .setDefaultPort(8888);
        this.client = WebClient.create(rxVertx, options);
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
                }
            );
    }

    @Test
    void testPostValidation(Vertx vertx, VertxTestContext testContext) {
        client.post("/posts")
            .rxSendJson(CreatePostCommand.of("test", ""))
            .subscribe(
                response -> {
                    var statusCode = response.statusCode();
                    var body = response.bodyAsString();
                    log.info("status code: {}, body: {}", statusCode, body);
                    assertThat(statusCode).isEqualTo(400);
                    assertThat(body).isEqualTo("validation failed.");
                    testContext.completeNow();
                }
            );
    }

}
