package com.example.demo;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
public class TestMainVerticle {

    HttpClient client;

    @BeforeEach
    void setup(Vertx vertx, VertxTestContext testContext) {
        vertx.deployVerticle(new MainVerticle(), testContext.succeeding(id -> testContext.completeNow()));
        var options = new HttpClientOptions()
            .setDefaultHost("localhost")
            .setDefaultPort(8080);
        client = vertx.createHttpClient(options);
    }

    @Test
    void getAllPosts(Vertx vertx, VertxTestContext testContext) throws Throwable {
        var query = """
            query {
                allPosts{
                    id
                    title
                    content
                }
            }""";
        client.request(HttpMethod.POST, "/graphql")
            .flatMap(req -> req.putHeader("Content-Type", "application/json")
                .putHeader("Accept", "application/json")
                .send(Json.encode(Map.of("query", query)))//have to use Json.encode to convert objects to json string.
                .flatMap(HttpClientResponse::body)
            )
            .onComplete(
                testContext.succeeding(buffer ->
                    testContext.verify(() -> {
                            log.info("buf: {}", buffer.toString());
                            JsonArray array = buffer.toJsonObject()
                                .getJsonObject("data")
                                .getJsonArray("allPosts");
                            assertThat(array.size()).isGreaterThan(0);

                            var titles = array.getList().stream().map(o -> ((Map<String, Object>) o).get("title")).toList();
                            assertThat(titles).allMatch(s -> ((String) s).startsWith("HELLO"));
                            testContext.completeNow();
                        }
                    )
                )
            );
    }

    @Test
    void createPost(Vertx vertx, VertxTestContext testContext) throws Throwable {
        String TITLE = "My post created by Vertx HttpClient";
        //var creatPostQuery = "mutation newPost($input:CreatePostInput!){ createPost(createPostInput:$input)}";
        var creatPostQuery = """
            mutation newPost($input:CreatePostInput!){
                createPost(createPostInput:$input)
            }""";
        client.request(HttpMethod.POST, "/graphql")
            .flatMap(req -> {
                    String encodedJson = Json.encode(Map.of(
                        "query", creatPostQuery,
                        "variables", Map.of(
                            "input", Map.of(
                                "title", TITLE,
                                "content", "content of my post"
                            )
                        )
                    ));
                    log.info("sending encoded json: {}", encodedJson);
                    return req.putHeader("Content-Type", "application/json")
                        .putHeader("Accept", "application/json")
                        .send(encodedJson)//have to use Json.encode to convert objects to json string.
                        .flatMap(HttpClientResponse::body);
                }
            )
            .flatMap(buf -> {
                log.debug("create post buf: {}", buf.toString());
                Object id = buf.toJsonObject().getJsonObject("data").getValue("createPost");

                log.info("created post: {}", id);
                assertThat(id).isNotNull();

                var postById = """
                    query post($id:String!) {
                        postById(postId:$id){id title content}
                    }""";

                return client.request(HttpMethod.POST, "/graphql")
                    .flatMap(req -> req.putHeader("Content-Type", "application/json")
                        .putHeader("Accept", "application/json")
                        .send(Json.encode(Map.of(
                            "query", postById,
                            "variables", Map.of("id", id.toString())
                        )))//have to use Json.encode to convert objects to json string.
                        .flatMap(HttpClientResponse::body)
                    );
            })
            .onComplete(
                testContext.succeeding(
                    buffer -> testContext.verify(
                        () -> {
                            log.info("buf: {}", buffer.toString());
                            String title = buffer.toJsonObject()
                                .getJsonObject("data")
                                .getJsonObject("postById")
                                .getString("title");
                            assertThat(title).isEqualTo(TITLE.toUpperCase());
                            testContext.completeNow();
                        }
                    )
                )
            );
    }
}
