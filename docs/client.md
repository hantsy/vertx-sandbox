# Consuming with RESTful APIs with Vertx HttpClient/WebClient

We have discussed how to build RESTful APIs with Eclipse Vertx Web and the RxJava 3 API bindings. In those posts, to serve the RESTful APIs,  you need to create a HTTP server from the `Vertx` object in the entry class - `MainVerticle`.

In this post, we will cover how to create a *Http Client* and interact with RESTful APIs.

Similar to the creating of HttpServer, you can create a HttpClient from `Vertx` instance.

```java
var options = new HttpClientOptions()
    .setDefaultPort(8888);
var client = vertx.createHttpClient(options);
```

Then you can send request to the server like the following.

```java
client.request(HttpMethod.GET, "/hello")
    .compose(req -> req.send().compose(HttpClientResponse::body))
    .onSuccess(...)
    .onFailure(...)
```

The `HttpClient` is a low-level APIs and provides find-grained control of the request and response info.   Vertx provides a more advanced API to shake hands with the server side, it is called `WebClient`.

Similar to the creating of `HttpClient`,  create a `WebClient` instance like this.

```java
var options = new WebClientOptions()
            .setDefaultHost("localhost")
            .setDefaultPort(8888);
var webclient = WebClient.create(vertx, options);
```

Or create a  `WebClient` from the existing `HttpClient` instance.

```java
var webclient = WebClient.wrap(httpClient, options);
```

An example using WebClient to send requests.

```java
client.get("/posts")
    .send()
    .onSuccess(...)
    .onFailure(...)
```

Compare to the `HttpClient` , it provides more comprehensive methods to send request body, eg. JSON object, multipart form, etc.

Next let's move to the testing of Vertx, and use HttpClient/WebClient to test RESTful API endpoints.

The following example is a simple JUnit 5 tests. 

```java
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

}
```



With the  `@ExtendWith(VertxExtension.class)`, it allows you to inject `Vertx` and `VertxTestContext` parameter in the test methods. The `VertxTestContext`  wrap a  `CountDownLatch` to indicate the asynchronous execution is done, you have to call the `completeNow` or `failNow` to end execution, else the test execution will be blocked till it is timeout.

Let's have a look at an example of testing the */posts* endpoint to get all posts.

```java
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
```

The following example is testing the `PostNotFoundException` which return a 404 HTTP status code.

```java
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
```

The client operations can be chained. The following is an example to test a  CRUD flow.

```java
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
```

Similarly, you can use `WebClient` in the tests.

The following is an example using `WebClient` with RxJava 3 bindings. 

```java
@Test
void testGetAll(Vertx vertx, VertxTestContext testContext) {
    client.get("/posts")
        .rxSend()
        .subscribe(
        response -> {
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.bodyAsJsonArray().size()).isEqualTo(2);

            testContext.completeNow();
        }
    );
}
```

The `WebClient` provides more fluent APIs.  In most of cases of sending a request it is easier than the raw HttpClient, esp. handling the multipart form. We will explore it in future.

