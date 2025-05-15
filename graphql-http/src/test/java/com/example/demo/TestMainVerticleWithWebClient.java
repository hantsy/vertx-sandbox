package com.example.demo;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.multipart.MultipartForm;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
@Slf4j
public class TestMainVerticleWithWebClient {

    WebClient client;

    @BeforeEach
    void setup(Vertx vertx, VertxTestContext testContext) {
        vertx.deployVerticle(new MainVerticle())
            .onComplete(testContext.succeeding(id -> {
                log.debug("Deployed: {}", id);
                var options = new WebClientOptions()
                    .setDefaultHost("localhost")
                    .setDefaultPort(8080);
                client = WebClient.create(vertx, options);
                testContext.completeNow();
            }));
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
        client.post("/graphql")
            .sendJson(Map.of("query", query))
            .map(HttpResponse::body)
            .onSuccess((Buffer buffer) -> {
                    log.info("buf: {}", buffer.toString());
                    JsonArray array = buffer.toJsonObject()
                        .getJsonObject("data")
                        .getJsonArray("allPosts");
                    assertThat(array.size()).isGreaterThan(0);

                    var titles = array.getList().stream().map(o -> ((Map<String, Object>) o).get("title")).toList();
                    assertThat(titles).anyMatch(s -> ((String) s).startsWith("HELLO"));
                    testContext.completeNow();
                }
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
        client.post("/graphql")
            .sendJson(Map.of(
                    "query", creatPostQuery,
                    "variables", Map.of(
                        "input", Map.of(
                            "title", TITLE,
                            "content", "content of my post"
                        )
                    )
                )
            )
            .map(HttpResponse::body)
            .flatMap(buf -> {
                log.debug("create post buf: {}", buf.toString());
                Object id = buf.toJsonObject().getJsonObject("data").getValue("createPost");

                log.info("created post: {}", id);
                assertThat(id).isNotNull();

                var postById = """
                    query post($id:String!) {
                        postById(postId:$id){id title content}
                    }""";

                return client.post("/graphql")
                    .sendJson(Map.of(
                            "query", postById,
                            "variables", Map.of("id", id.toString())
                        )
                    )
                    .map(HttpResponse::body);
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

    //@Test
    public void testFileUpload(Vertx vertx, VertxTestContext testContext) throws Exception {
        Buffer fileBuf = vertx.fileSystem().readFileBlocking("test.txt");
        MultipartForm form = MultipartForm.create();
        String query = """
            mutation upload($file:Upload!){
                upload(file:$file)
            }
            """;
        var variables = new HashMap<String, Object>();
        variables.put("file", null);
        form.attribute("operations", Json.encode(Map.of("query", query, "variables", variables)));
        form.attribute("map", Json.encode(Map.of("file0", List.of("variables.file"))));
        form.textFileUpload("file0", "test.txt", fileBuf, "text/plain");

        client.post("/graphql")
            .sendMultipartForm(form)
            .onSuccess(
                data -> log.info("data of upload: {}", data.bodyAsString())
            )
            .onFailure(e -> log.error("error: {}", e));
    }
}
