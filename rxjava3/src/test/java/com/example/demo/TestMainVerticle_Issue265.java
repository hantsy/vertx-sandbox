package com.example.demo;

import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.core.http.HttpClient;
import io.vertx.rxjava3.core.http.HttpClientRequest;
import io.vertx.rxjava3.core.http.HttpClientResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
public class TestMainVerticle_Issue265 {
    HttpClient client;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {

        // see https://github.com/vert-x3/vertx-rx/issues/265
        // `testContext.succeeding` will block application.
        vertx.deployVerticle(new MainVerticle())
            .subscribe(
                data -> testContext.verify(() -> {
                    log.info("deployed: {}", data);
                    //
                    // The following line creates a handler that is never used
                    //testContext.succeeding(id -> testContext.completeNow());

                    testContext.completeNow();
                }),
                error -> {
                    log.error("error: {}", error);
                    // Without this line the test will timeout in case of failure to deploy the verticle
                    testContext.failNow(error);
                }
            );

        // build HttpClient
        HttpClientOptions clientOptions = new HttpClientOptions()
            .setDefaultPort(8888);
        client = vertx.createHttpClient(clientOptions);

    }

    @Test
    void testGetAll(Vertx vertx, VertxTestContext testContext) {
        client.request(HttpMethod.GET, "/posts")
            .flatMap(HttpClientRequest::send)
            .flatMap(HttpClientResponse::rxBody)
            .subscribe(
                response -> {
                    assertThat(response.toJsonArray().size()).isEqualTo(2);
                    testContext.completeNow();
                },
                testContext::failNow
            );
    }


}
