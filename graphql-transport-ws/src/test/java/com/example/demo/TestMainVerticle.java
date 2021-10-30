package com.example.demo;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.Map;

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
    public void testCreatePost(VertxTestContext testContext) {
        var titleReplay = new ArrayList<String>();
        client.webSocket("/graphql")
                .onSuccess(webSocket -> {

                    webSocket.closeHandler(v -> log.info("websocket is being closed"));
                    webSocket.endHandler(v -> log.info("websocket is being ended"));
                    webSocket.exceptionHandler(e -> log.info("catching websocket exception: {}", e.getMessage()));

                    webSocket.textMessageHandler(text -> {
                        //log.info("websocket message handler:{}", text);
                        JsonObject obj = new JsonObject(text);
                        String type = obj.getString("type");
                        if (type.equals("connection_ack")) {
                            return;// do nothing when ka.
                        } else if (type.equals("data")) {
                            // handle the subscription `commentAdded` data.
                            var title = obj.getJsonObject("payload").getJsonObject("data").getJsonObject("createPost").getString("title");
                            log.info("subscription commentAdded data: {}", title);
                            titleReplay.add(title);
                        }
                    });


                    JsonObject messageInit = new JsonObject()
                            .put("type", "connection_init")//this is required to initialize a connection.
                            .put("id", "1");
                    JsonObject createPostData = new JsonObject()
                            .put("payload", new JsonObject()
                                    .put("query", "mutation newPost($input:CreatePostInput!){ createPost(createPostInput:$input)}")
                                    .put("variables", Map.of(
                                                    "input", Map.of("title", "test title", "content", "content of the new post")
                                            )
                                    )
                            )
                            .put("type", "start")
                            .put("id", "1");
                    webSocket.write(messageInit.toBuffer());
                    webSocket.write(createPostData.toBuffer());

                    try {
                        Thread.sleep(1000L);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    testContext.completeNow();
                })
                .onFailure(e -> {
                    log.error("error:" + e);
                    testContext.failNow(e);
                });

        assertThat(titleReplay.size()).isEqualTo(1);
        assertThat(titleReplay.get(0)).isEqualTo("test title");
    }
}
