package com.example.demo;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.WebSocketClientOptions;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.handler.graphql.ApolloWSMessageType;
import io.vertx.ext.web.multipart.MultipartForm;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.vertx.ext.web.handler.graphql.ApolloWSMessageType.CONNECTION_KEEP_ALIVE;
import static io.vertx.ext.web.handler.graphql.ApolloWSMessageType.DATA;

@Slf4j
public class MainVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise) throws Exception {

        var options = new WebClientOptions()
            .setUserAgent(WebClientOptions.loadUserAgent())
            .setDefaultHost("localhost")
            .setDefaultPort(8080);

        var client = WebClient.create(vertx, options);

        uploadFile(client);
        //createPostAndAddComments(client);
        //getAllPosts(client);


//        vertx.createHttpServer().requestHandler(req -> {
//            req.response()
//                .putHeader("content-type", "text/plain")
//                .end("Hello from Vert.x!");
//        }).listen(8888, http -> {
//            if (http.succeeded()) {
//                startPromise.complete();
//                System.out.println("HTTP server started on port 8888");
//            } else {
//                startPromise.fail(http.cause());
//            }
//        });

        startPromise.complete();
    }

    private void createPostAndAddComments(WebClient client) {
        client.post("/graphql")
            .sendJson(Map.of(
                "query", "mutation newPost($input:CreatePostInput!){ createPost(createPostInput:$input)}",
                "variables", Map.of(
                    "input", Map.of("title", "create post from WebClient", "content", "content of the new post")
                )
            ))
            .onSuccess(
                data -> {
                    log.info("data of createPost: {}", data.bodyAsString());
                    var createdId = data.bodyAsJsonObject().getJsonObject("data").getString("createPost");
                    // get the created post.
                    getPostById(client, createdId);
                    // add comment.
                    addComment(client, createdId);
                }
            )
            .onFailure(e -> log.error("error: {}", e));
    }

    private void addComment(WebClient client, String id) {


        // switch to HttpClient to handle WebSocket
        var options = new WebSocketClientOptions()
            .setClosingTimeout(7200)
            .setDefaultHost("localhost")
            .setDefaultPort(8080);

        // see: https://github.com/vert-x3/vertx-web/blob/master/vertx-web-graphql/src/test/java/io/vertx/ext/web/handler/graphql/ApolloWSHandlerTest.java
        var webSocketClient = vertx.createWebSocketClient(options);
        webSocketClient.connect("/graphql")
            .onSuccess(ws -> {
                ws.closeHandler(v -> log.info("websocket is being closed"));
                ws.endHandler(v -> log.info("websocket is being ended"));
                ws.exceptionHandler(e -> log.info("catching websocket exception: {}", e.getMessage()));

                ws.textMessageHandler(text -> {
                    //log.info("websocket message handler:{}", text);
                    JsonObject obj = new JsonObject(text);
                    ApolloWSMessageType type = ApolloWSMessageType.from(obj.getString("type"));
                    if (type.equals(CONNECTION_KEEP_ALIVE)) {
                        return;// do nothing when ka.
                    } else if (type.equals(DATA)) {
                        // handle the subscription `commentAdded` data.
                        log.info("subscription commentAdded data: {}", obj.getJsonObject("payload").getJsonObject("data").getJsonObject("commentAdded"));
                    }
                });

                JsonObject messageInit = new JsonObject()
                    .put("type", "connection_init")//this is required to initialize a connection.
                    .put("id", "1");

                JsonObject message = new JsonObject()
                    .put("payload", new JsonObject()
                        .put("query", "subscription onCommentAdded { commentAdded { id content } }"))
                    .put("type", "start")
                    .put("id", "1");

                ws.write(messageInit.toBuffer());
                ws.write(message.toBuffer());
            })
            .onFailure(e -> log.error("error: {}", e));

        addCommentToPost(client, id);
        addCommentToPost(client, id);
        addCommentToPost(client, id);
    }

    private void addCommentToPost(WebClient client, String id) {
        client.post("/graphql")
            .sendJson(Map.of(
                "query", "mutation addComment($input:CommentInput!){ addComment(commentInput:$input) }",
                "variables", Map.of(
                    "input", Map.of(
                        "postId", id,
                        "content", "comment content of post id" + LocalDateTime.now()
                    )
                )
            ))
            .onSuccess(
                data -> log.info("data of addComment: {}", data.bodyAsString())
            )
            .onFailure(e -> log.error("error: {}", e));
    }

    private void getPostById(WebClient client, String id) {
        client.post("/graphql")
            .sendJson(Map.of(
                "query", "query post($id:String!){ postById(postId:$id){ id title content author{ name } comments{ content createdAt} createdAt}}",
                "variables", Map.of(
                    "id", id
                )
            ))
            .onSuccess(
                data -> log.info("data of postByID: {}", data.bodyAsString())
            )
            .onFailure(e -> log.error("error: {}", e));
    }

    private void getAllPosts(WebClient client) {
        client.post("/graphql")
            .sendJson(Map.of(
                "query", "query posts{ allPosts{ id title content author{ name } comments{ content createdAt} createdAt}}",
                "variables", Map.of()
            ))
            .onSuccess(
                data -> log.info("data of allPosts: {}", data.bodyAsString())
            )
            .onFailure(e -> log.error("error: {}", e));
    }

    private void uploadFile(WebClient client) {
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
