package com.example.demo;

import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
public class TestMainVerticle_Issue265 {
    WebClient client;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {

        // see https://github.com/vert-x3/vertx-rx/issues/265
        // this will block application.
        vertx.deployVerticle(new MainVerticle())
            .subscribe(
                data -> {
                    log.info("deployed: {}", data);
                    // The following line creates a handler that is never used
                    //testContext.succeeding(id -> testContext.completeNow());

                    testContext.completeNow();
                },
                error -> {
                    log.error("error: {}", error);
                    // Without this line the test will timeout in case of failure to deploy the verticle
                    testContext.failNow(error);
                }
            );

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
                }
            );
    }


}
