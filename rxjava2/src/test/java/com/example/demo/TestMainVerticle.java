package com.example.demo;



import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.logging.Level;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
public class TestMainVerticle {
    private static final Logger LOGGER = Logger.getLogger(TestMainVerticle.class.getName());

    @BeforeEach
    void deploy_verticle(Vertx vertx, VertxTestContext testContext) {
        vertx.deployVerticle(new MainVerticle(), testContext.succeeding(id -> testContext.completeNow()));
    }

    @Test
    void verticle_deployed(Vertx vertx, VertxTestContext testContext) throws Throwable {
        testContext.completeNow();
    }

    @Test
    void testGetAll(Vertx vertx, VertxTestContext testContext) throws Throwable {
        WebClient client = WebClient.create(vertx);
        client.get(8888, "localhost", "/posts")
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
    void testPostValidation(Vertx vertx, VertxTestContext testContext) throws Throwable {
        WebClient client = WebClient.create(vertx);
        client.post(8888, "localhost", "/posts")
            .rxSendJson(PostForm.of("test", ""))
            .subscribe(
                response -> {
                    var statusCode = response.statusCode();
                    var body = response.bodyAsString();
                    LOGGER.log(Level.INFO, "status code: {0}, body: {1}", new Object[] {
                        statusCode, body
                    });
                    assertThat(statusCode).isEqualTo(400);
                    assertThat(body).isEqualTo("validation failed.");
                    testContext.completeNow();
                }
            );
    }

}
