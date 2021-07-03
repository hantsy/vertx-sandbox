package com.example.demo;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.core.json.Json;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
public class TestMainVerticle {
    private final static Logger LOGGER = Logger.getLogger(TestMainVerticle.class.getName());
    HttpClient client;

    @BeforeEach
    void setup(Vertx vertx, VertxTestContext testContext) {
        vertx.deployVerticle(new MainVerticle(), testContext.succeeding(id -> testContext.completeNow()));
        var options = new HttpClientOptions()
            .setDefaultPort(8888);
        this.client = vertx.createHttpClient(options);
    }

    // Repeat this test 3 times
    @RepeatedTest(3)
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Check the HTTP response...")
    void testHello(Vertx vertx, VertxTestContext testContext) {
        client.request(HttpMethod.GET, "/hello")
            .compose(req -> req.send().compose(HttpClientResponse::body))
            .onComplete(
                testContext.succeeding(
                    buffer -> testContext.verify(
                        () -> {
                            assertThat(buffer.toString()).isEqualTo("Hello from my route");
                            testContext.completeNow();
                        }
                    )
                )
            );
    }

    @AfterEach
    @DisplayName("Check that the verticle is still there")
    void lastChecks(Vertx vertx) {
        assertThat(vertx.deploymentIDs())
            .isNotEmpty()
            .hasSize(1);
    }

    @Test
    void testGetAll(Vertx vertx, VertxTestContext testContext) {
        client.request(HttpMethod.GET, "/posts")
            .flatMap(HttpClientRequest::send)
            .flatMap(HttpClientResponse::body)
            .onComplete(
                testContext.succeeding(
                    buffer -> testContext.verify(
                        () -> {
                            assertThat(buffer.toJsonArray().size()).isEqualTo(2);
                            testContext.completeNow();
                        }
                    )
                )
            );
    }


    @Test
    void testGetByNoneExistingId(Vertx vertx, VertxTestContext testContext) {
        var postByIdUrl = "/posts/" + UUID.randomUUID();
        client.request(HttpMethod.GET, postByIdUrl)
            .flatMap(HttpClientRequest::send)
            .onComplete(
                testContext.succeeding(
                    response -> testContext.verify(
                        () -> {
                            assertThat(response.statusCode()).isEqualTo(404);
                            testContext.completeNow();
                        }
                    )
                )
            );
    }

    @Test
    void testUpdateByNoneExistingId(Vertx vertx, VertxTestContext testContext) {
        var postByIdUrl = "/posts/" + UUID.randomUUID();
        client.request(HttpMethod.PUT, postByIdUrl)
            .flatMap(req -> req.putHeader("Content-Type", "application/json")
                .send(Json.encode(CreatePostCommand.of("test title", "test content of my post")))
            )
            .onComplete(
                testContext.succeeding(
                    response -> testContext.verify(
                        () -> {
                            assertThat(response.statusCode()).isEqualTo(404);
                            testContext.completeNow();
                        }
                    )
                )
            );
    }

    @Test
    void testDeleteByNoneExistingId(Vertx vertx, VertxTestContext testContext) {
        var postByIdUrl = "/posts/" + UUID.randomUUID();
        client.request(HttpMethod.DELETE, postByIdUrl)
            .flatMap(HttpClientRequest::send)
            .onComplete(
                testContext.succeeding(
                    response -> testContext.verify(
                        () -> {
                            assertThat(response.statusCode()).isEqualTo(404);
                            testContext.completeNow();
                        }
                    )
                )
            );
    }

    @Test
    void testCreatePost(Vertx vertx, VertxTestContext testContext) {
        client.request(HttpMethod.POST, "/posts")
            .flatMap(req -> req.putHeader("Content-Type", "application/json")
                .send(Json.encode(CreatePostCommand.of("test title", "test content of my post")))
                .onSuccess(
                    response -> assertThat(response.statusCode()).isEqualTo(201)
                )
            )
            .flatMap(response -> {
                String location = response.getHeader("Location");
                LOGGER.log(Level.INFO, "location header: {0}", location);
                assertThat(location).isNotNull();

                return Future.succeededFuture(location);
            })
            .flatMap(url -> client.request(HttpMethod.GET, url)
                .flatMap(HttpClientRequest::send)
                .onSuccess(response -> {
                    LOGGER.log(Level.INFO, "http status: {0}", response.statusCode());
                    assertThat(response.statusCode()).isEqualTo(200);
                })
                .flatMap(res -> client.request(HttpMethod.PUT, url)
                    .flatMap(req -> req.putHeader("Content-Type", "application/json")
                        .send(Json.encode(CreatePostCommand.of("updated test title", "updated test content of my post")))
                    )
                    .onSuccess(response -> {
                        LOGGER.log(Level.INFO, "http status: {0}", response.statusCode());
                        assertThat(response.statusCode()).isEqualTo(204);
                    })
                )
                .flatMap(res -> client.request(HttpMethod.GET, url)
                    .flatMap(HttpClientRequest::send)
                    .onSuccess(response -> {
                        LOGGER.log(Level.INFO, "http status: {0}", response.statusCode());
                        assertThat(response.statusCode()).isEqualTo(200);

                    })
                    .flatMap(HttpClientResponse::body)
                    .onSuccess(body -> assertThat(body.toJsonObject().getString("title")).isEqualTo("updated test title"))
                )
                .flatMap(res -> client.request(HttpMethod.DELETE, url)
                    .flatMap(HttpClientRequest::send)
                    .onSuccess(response -> {
                        LOGGER.log(Level.INFO, "http status: {0}", response.statusCode());
                        assertThat(response.statusCode()).isEqualTo(204);
                    })
                )
                .flatMap(res -> client.request(HttpMethod.GET, url)
                    .flatMap(HttpClientRequest::send)
                    .onSuccess(response -> {
                        LOGGER.log(Level.INFO, "http status: {0}", response.statusCode());
                        assertThat(response.statusCode()).isEqualTo(404);
                    })
                )
            )
            .onComplete(
                testContext.succeeding(id -> testContext.completeNow())
            );
    }

}
