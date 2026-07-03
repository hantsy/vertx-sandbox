# Consuming RESTful APIs with Vert.x HttpClient/WebClient

We have discussed how to build RESTful APIs with Eclipse Vert.x Web in the [web module](../start/rest.md). In this post, we cover how to create an **HttpClient** and **WebClient** to interact with those RESTful APIs.

The project uses **Vert.x 5.1.3**, **Java 25**, and the **Launcher** approach (`io.vertx.launcher.application.VertxApplication`). It uses `Pool`/`PgBuilder` for database access and Jackson BOM (`jackson-bom`, 2.22.0) for version alignment.

Checkout the [complete sample codes from my Github](https://github.com/hantsy/vertx-sandbox/tree/master/web).

## HttpClient (low-level)

Create a `HttpClient` from the `Vertx` instance:

```java
var options = new HttpClientOptions()
    .setDefaultPort(8888);
var client = vertx.createHttpClient(options);
```

Send a request:

```java
client.request(HttpMethod.GET, "/hello")
    .compose(req -> req.send().compose(HttpClientResponse::body))
    .onSuccess(body -> log.info("response: {}", body))
    .onFailure(e -> log.error("error: {}", e));
```

## WebClient (high-level)

`WebClient` provides a more fluent API on top of `HttpClient`:

```java
var options = new WebClientOptions()
    .setDefaultHost("localhost")
    .setDefaultPort(8888);
var webclient = WebClient.create(vertx, options);

// Or wrap an existing HttpClient
var webclient = WebClient.wrap(httpClient, options);

client.get("/posts")
    .send()
    .onSuccess(response -> log.info("response: {}", response.bodyAsString()))
    .onFailure(e -> log.error("error: {}", e));
```

Compared to `HttpClient`, `WebClient` provides more comprehensive methods to send request bodies — JSON objects, multipart forms, etc.

## Testing with HttpClient

Tests use `@ExtendWith(VertxExtension.class)` from `vertx-junit5` to inject `Vertx` and `VertxTestContext` parameters. The `VertxTestContext` wraps a `CountDownLatch` — call `completeNow()` or `failNow()` to signal completion.

In Vert.x 5, `vertx.deployVerticle()` returns a `Future` directly, which uses the fluent `onComplete(testContext.succeeding(...))` pattern:

```java
@ExtendWith(VertxExtension.class)
public class TestMainVerticle {
    private static final Logger LOGGER = Logger.getLogger(TestMainVerticle.class.getName());
    HttpClient client;

    @BeforeEach
    void setup(Vertx vertx, VertxTestContext testContext) {
        vertx.deployVerticle(new MainVerticle())
            .onComplete(testContext.succeeding(id -> {
                LOGGER.log(Level.INFO, "deployed:{0}", id);
                var options = new HttpClientOptions().setDefaultPort(8888);
                this.client = vertx.createHttpClient(options);
                testContext.completeNow();
            }));
    }

    @RepeatedTest(3)
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Check the HTTP response...")
    void testHello(Vertx vertx, VertxTestContext testContext) {
        client.request(HttpMethod.GET, "/hello")
            .compose(req -> req.send().compose(HttpClientResponse::body))
            .onComplete(testContext.succeeding(
                buffer -> testContext.verify(() -> {
                    assertThat(buffer.toString()).contains("Hello");
                    testContext.completeNow();
                })
            ));
    }
}
```

### Testing GET all posts

```java
@Test
void testGetAll(Vertx vertx, VertxTestContext testContext) {
    client.request(HttpMethod.GET, "/posts")
        .flatMap(HttpClientRequest::send)
        .flatMap(HttpClientResponse::body)
        .onComplete(testContext.succeeding(
            buffer -> testContext.verify(() -> {
                assertThat(buffer.toJsonArray().size()).isEqualTo(2);
                testContext.completeNow();
            })
        ));
}
```

### Testing 404 Not Found

```java
@Test
void testGetByNoneExistingId(Vertx vertx, VertxTestContext testContext) {
    var postByIdUrl = "/posts/" + UUID.randomUUID();
    client.request(HttpMethod.GET, postByIdUrl)
        .flatMap(HttpClientRequest::send)
        .onComplete(testContext.succeeding(
            response -> testContext.verify(() -> {
                assertThat(response.statusCode()).isEqualTo(404);
                testContext.completeNow();
            })
        ));
}
```

### Testing a full CRUD flow (chained operations)

```java
@Test
void testCreatePost(Vertx vertx, VertxTestContext testContext) {
    client.request(HttpMethod.POST, "/posts")
        .flatMap(req -> req.putHeader("Content-Type", "application/json")
            .send(Json.encode(CreatePostCommand.of("test title", "test content of my post")))
            .onSuccess(response -> assertThat(response.statusCode()).isEqualTo(201))
        )
        .flatMap(response -> {
            String location = response.getHeader("Location");
            assertThat(location).isNotNull();
            return Future.succeededFuture(location);
        })
        .flatMap(url -> client.request(HttpMethod.GET, url)
            .flatMap(HttpClientRequest::send)
            .onSuccess(response -> assertThat(response.statusCode()).isEqualTo(200))
            .flatMap(res -> client.request(HttpMethod.PUT, url)
                .flatMap(req -> req.putHeader("Content-Type", "application/json")
                    .send(Json.encode(CreatePostCommand.of("updated test title", "updated test content of my post")))
                )
                .onSuccess(response -> assertThat(response.statusCode()).isEqualTo(204))
            )
            .flatMap(res -> client.request(HttpMethod.GET, url)
                .flatMap(HttpClientRequest::send)
                .onSuccess(response -> assertThat(response.statusCode()).isEqualTo(200))
                .flatMap(HttpClientResponse::body)
                .onSuccess(body -> assertThat(body.toJsonObject().getString("title")).isEqualTo("updated test title"))
            )
            .flatMap(res -> client.request(HttpMethod.DELETE, url)
                .flatMap(HttpClientRequest::send)
                .onSuccess(response -> assertThat(response.statusCode()).isEqualTo(204))
            )
            .flatMap(res -> client.request(HttpMethod.GET, url)
                .flatMap(HttpClientRequest::send)
                .onSuccess(response -> assertThat(response.statusCode()).isEqualTo(404))
            )
        )
        .onComplete(testContext.succeeding(id -> testContext.completeNow()));
}
```

Operations are chained via `.flatMap()` — each step depends on the result of the previous one.

Get the [source codes from my Github](https://github.com/hantsy/vertx-sandbox/tree/master/web).

