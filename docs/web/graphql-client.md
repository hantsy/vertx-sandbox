# Consuming GraphQL APIs with Vert.x HttpClient/WebClient

In [a previous post](./client.md), we covered consuming RESTful APIs with Vert.x HttpClient and WebClient. In this post, we will consume the GraphQL APIs created in the [graphql-http](./graphql-http.md) and [graphql-transport-ws](./graphql-transport-ws.md) modules.

Vert.x does not provide a dedicated GraphQL client library like some other frameworks. Instead, you use the generic Vert.x **HttpClient** (low-level) or **WebClient** (high-level) to interact with GraphQL endpoints over HTTP. For WebSocket-based GraphQL (subscriptions), you use the Vert.x **WebSocketClient**.

Checkout the [complete sample codes from my Github](https://github.com/hantsy/vertx-sandbox/tree/master/graphql-http) (tests) and [graphql-transport-ws](https://github.com/hantsy/vertx-sandbox/tree/master/graphql-transport-ws) (tests).

## GraphQL over HTTP

Refer to the [GraphQL over HTTP specification](https://graphql.org/learn/serving-over-http/) and [GraphQL multipart request specification](https://github.com/jaydenseric/graphql-multipart-request-spec) for the wire format.

The request body follows this JSON structure:

```json
{
  "query": "...",
  "operationName": "...",
  "variables": { "myVariable": "someValue", ... }
}
```

And the response body is:

```json
{
  "data": "...",
  "errors": "..."
}
```

When an error occurs, the `errors` field contains error details. Unlike RESTful APIs, the HTTP response status code is typically 200 even when application-level errors occur.

## Using HttpClient (low-level)

The following example is from the [graphql-http test](./graphql-http.md#testing), which sends queries and mutations via `POST /graphql`:

```java
@ExtendWith(VertxExtension.class)
@Slf4j
public class TestMainVerticle {
    HttpClient client;

    @BeforeEach
    void setup(Vertx vertx, VertxTestContext testContext) {
        vertx.deployVerticle(new MainVerticle())
            .onComplete(testContext.succeeding(id -> {
                var options = new HttpClientOptions()
                    .setDefaultHost("localhost")
                    .setDefaultPort(8080);
                client = vertx.createHttpClient(options);
                testContext.completeNow();
            }));
    }

    @Test
    void getAllPosts(Vertx vertx, VertxTestContext testContext) {
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
                .send(Json.encode(Map.of("query", query)))
                .flatMap(HttpClientResponse::body)
            )
            .onComplete(testContext.succeeding(buffer ->
                testContext.verify(() -> {
                    JsonArray array = buffer.toJsonObject()
                        .getJsonObject("data")
                        .getJsonArray("allPosts");
                    assertThat(array.size()).isGreaterThan(0);
                    testContext.completeNow();
                })
            ));
    }
}
```

The key aspects are:
1. Set `Content-Type: application/json` and `Accept: application/json` headers
2. Encode the request body as JSON with the `query` field (and optional `variables` and `operationName`)
3. Send via `POST /graphql`

## Using WebClient (high-level)

`WebClient` provides a more fluent API for sending requests, including JSON payloads and multipart forms:

```java
var options = new WebClientOptions()
    .setUserAgent(WebClientOptions.loadUserAgent())
    .setDefaultHost("localhost")
    .setDefaultPort(8080);

var client = WebClient.create(vertx, options);

client.post("/graphql")
    .sendJson(Map.of(
        "query", "query posts{ allPosts{ id title content author{ name } comments{ content createdAt} createdAt}}",
        "variables", Map.of()
    ))
    .onSuccess(data -> log.info("data of allPosts: {}", data.bodyAsString()))
    .onFailure(e -> log.error("error: {}", e));
```

### Mutations with Variables

```java
client.post("/graphql")
    .sendJson(Map.of(
        "query", "mutation newPost($input:CreatePostInput!){ createPost(createPostInput:$input)}",
        "variables", Map.of(
            "input", Map.of("title", "create post from WebClient", "content", "content of the new post")
        )
    ))
    .onSuccess(data -> {
        var createdId = data.bodyAsJsonObject()
            .getJsonObject("data").getString("createPost");
        log.info("created post id: {}", createdId);
    })
    .onFailure(e -> log.error("error: {}", e));
```

### File Uploads

File uploads follow the [GraphQL multipart request specification](https://github.com/jaydenseric/graphql-multipart-request-spec) and use `MultipartForm`:

```java
private void uploadFile(WebClient client) {
    Buffer fileBuf = vertx.fileSystem().readFileBlocking("test.txt");
    MultipartForm form = MultipartForm.create();

    String query = """
        mutation upload($file:Upload!){
            upload(file:$file)
        }
    """;

    form.attribute("operations", Json.encode(Map.of("query", query, "variables", Map.of("file", null))));
    form.attribute("map", Json.encode(Map.of("file0", List.of("variables.file"))));
    form.textFileUpload("file0", "test.txt", fileBuf, "text/plain");

    client.post("/graphql")
        .sendMultipartForm(form)
        .onSuccess(data -> log.info("data of upload: {}", data.bodyAsString()))
        .onFailure(e -> log.error("error: {}", e));
}
```

## Using WebSocketClient for graphql-transport-ws

For the [graphql-transport-ws](./graphql-transport-ws.md) variant, all operations flow through a WebSocket connection using the dedicated `WebSocketClient`:

```java
var options = new WebSocketClientOptions()
    .setConnectTimeout(7200)
    .setDefaultHost("localhost")
    .setDefaultPort(8080);
var client = vertx.createWebSocketClient(options);

var connectOptions = new WebSocketConnectOptions()
    .addSubProtocol("graphql-transport-ws")
    .setURI("/graphql");

client.connect(connectOptions)
    .onSuccess(socket -> {
        socket.textMessageHandler(text -> {
            JsonObject obj = new JsonObject(text);
            String type = obj.getString("type");
            if (type.equals("next")) {
                // handle subscription data
                log.info("received: {}", obj.getJsonObject("payload"));
            }
        });

        // Initialize connection
        JsonObject init = new JsonObject()
            .put("type", "connection_init");
        socket.write(init.toBuffer());

        // Subscribe to a mutation/query
        JsonObject subscribe = new JsonObject()
            .put("payload", new JsonObject()
                .put("query", "mutation { createPost(createPostInput: {title: \"test\", content: \"content\"}) }")
            )
            .put("type", "subscribe")
            .put("id", "1");
        socket.write(subscribe.toBuffer());
    });
```

See the [graphql-transport-ws test](./graphql-transport-ws.md#testing) for a complete working example.

## Summary

| Transport | Client | Use Case |
|-----------|--------|----------|
| HTTP (`graphql-http`) | `HttpClient` / `WebClient` | Queries, mutations, file uploads, subscriptions (via SSE) |
| WebSocket (`graphql-transport-ws`) | `WebSocketClient` | All operations over WebSocket, real-time subscriptions |

Get the [sample codes from my Github](https://github.com/hantsy/vertx-sandbox/tree/master/graphql-http).





