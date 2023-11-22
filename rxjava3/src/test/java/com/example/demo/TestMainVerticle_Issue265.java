package com.example.demo;

import io.reactivex.rxjava3.subscribers.TestSubscriber;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.core.http.HttpClient;
import io.vertx.rxjava3.core.http.HttpClientRequest;
import io.vertx.rxjava3.core.http.HttpClientResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
public class TestMainVerticle_Issue265 {
    HttpClient client;

    @SneakyThrows
    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {

        // see https://github.com/vert-x3/vertx-rx/issues/265
        // `testContext.succeeding` will block application.
        var latch = new CountDownLatch(1);
        vertx.rxDeployVerticle(new MainVerticle())
            .doOnTerminate(latch::countDown)
            .subscribe(
                data -> testContext.verify(() -> {
                    log.info("deployed: {}", data);
                    // The following line creates a handler that is never used
                    // testContext.succeeding(id -> testContext.completeNow());
                    testContext.completeNow();
                }),
                error -> {
                    log.error("error: {}", error);
                    // Without this line the test will timeout in case of failure to deploy the verticle
                    testContext.failNow(error);
                }
            );

        latch.await(500, TimeUnit.MILLISECONDS);

        // build HttpClient
        HttpClientOptions clientOptions = new HttpClientOptions()
            .setDefaultPort(8888);
        client = vertx.createHttpClient(clientOptions);
    }

    @Test
    void testGetAll(Vertx vertx, VertxTestContext testContext) {
        //var testSubscriber = TestSubscriber.create();
        client.request(HttpMethod.GET, "/posts")
            .flatMap(HttpClientRequest::send)
            .flatMap(HttpClientResponse::rxBody)
           // .subscribe(testSubscriber);
            .subscribe(
                response -> testContext.verify(() -> {
                    log.debug("response body: {}", response.toString());
                    assertThat(response.toJsonArray().size()).isEqualTo(2);
                    testContext.completeNow();
                }),
                testContext::failNow
            );
    }


}
