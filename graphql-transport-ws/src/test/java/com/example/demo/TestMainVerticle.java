package com.example.demo;

import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketClientOptions;
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
    WebSocketClient client;

    @BeforeEach
    void setup(Vertx vertx, VertxTestContext testContext) {
        vertx.deployVerticle(new MainVerticle())
                .onComplete(testContext.succeeding(id -> {
                    log.debug("Deployed: {}", id);
                    // switch to WebSocketClient to handle WebSocket
                    var options = new WebSocketClientOptions()
                            .setConnectTimeout(7200)
                            .setDefaultHost("localhost")
                            .setDefaultPort(8080);

                    client = vertx.createWebSocketClient(options);
                    testContext.completeNow();
                }));

    }

    @Test
    public void testCreatePost(VertxTestContext testContext) throws InterruptedException {
        var createdPostReplay = new ArrayList<String>();
        CountDownLatch latch = new CountDownLatch(1);
        var connectOptions = new WebSocketConnectOptions()
                .addSubProtocol("graphql-transport-ws")
                .setURI("/graphql");
        client.connect(connectOptions)
                .onSuccess(socket -> {

                    socket.closeHandler(v -> log.info("websocket is being closed"));
                    socket.endHandler(v -> log.info("websocket is being ended"));
                    socket.exceptionHandler(e -> log.info("catching websocket exception: {}", e.getMessage()));

                    socket.textMessageHandler(text -> {
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
                    socket.write(messageInit.toBuffer());
                    socket.write(createPostData.toBuffer());

                    testContext.completeNow();
                })
                .onFailure(e -> {
                    log.error("error:" + e);
                    testContext.failNow(e);
                });

        latch.await(5000, TimeUnit.MILLISECONDS);
        assertThat(createdPostReplay).hasSize(1);
        assertThat(createdPostReplay.getFirst()).isNotNull();
    }
}
