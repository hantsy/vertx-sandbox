package com.example.demo;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
public class TestMainVerticle {

    HttpClient client;

    @BeforeEach
    void setup(Vertx vertx, VertxTestContext testContext) {
        vertx.deployVerticle(new MainVerticle(), testContext.succeeding(id -> testContext.completeNow()));
        // switch to HttpClient to handle WebSocket
        var options = new HttpClientOptions()
                .setWebSocketClosingTimeout(7200)
                .setDefaultHost("localhost")
                .setDefaultPort(8080);

        client = vertx.createHttpClient(options);
    }

    @Test
    public void testCreatePost(VertxTestContext testContext) throws InterruptedException {
        var createdPostReplay = new ArrayList<String>();
        var options = new WebSocketConnectOptions()
                .addSubProtocol("graphql-transport-ws")
                .setURI("/graphql");
        CountDownLatch latch = new CountDownLatch(1);
        client.webSocket(options)
                .onSuccess(webSocket -> {

                    webSocket.closeHandler(v -> log.info("websocket is being closed"));
                    webSocket.endHandler(v -> log.info("websocket is being ended"));
                    webSocket.exceptionHandler(e -> log.info("catching websocket exception: {}", e.getMessage()));

                    webSocket.textMessageHandler(text -> {
                        log.info("handling websocket message: {}", text);
                        JsonObject obj = new JsonObject(text);
                        String type = obj.getString("type");
                        if (type.equals("connection_ack")) {
                            return;// do nothing when ka.
                        } else if (type.equals("next")) {
                            // handle the subscription `commentAdded` data.
                            var id = obj.getJsonObject("payload").getJsonObject("data").getString("createPost");
                            log.info("created post id: {}", id);
                            createdPostReplay.add(id);
                        }
                        latch.countDown();
                    });


                    JsonObject messageInit = new JsonObject()
                            .put("type", "connection_init")//this is required to initialize a connection.
                            .put("id", "1");
                    JsonObject createPostData = new JsonObject()
                            .put("payload", new JsonObject()
                                    .put("query", "mutation newPost($input:CreatePostInput!){ createPost(createPostInput:$input)}")
                                    .put("variables", new JsonObject()
                                            .put(
                                                    "input", new JsonObject()
                                                            .put("title", "test title")
                                                            .put("content", "content of the new post")
                                            )
                                    )
                            )
                            .put("type", "subscribe")
                            .put("id", "1");
                    webSocket.write(messageInit.toBuffer());
                    webSocket.write(createPostData.toBuffer());

                    testContext.completeNow();
                })
                .onFailure(e -> {
                    log.error("error:" + e);
                    testContext.failNow(e);
                });

        latch.await(5000, TimeUnit.MILLISECONDS);
        assertThat(createdPostReplay.size()).isEqualTo(1);
        assertThat(createdPostReplay.get(0)).isNotNull();
    }
}
