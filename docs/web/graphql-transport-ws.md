# Building GraphQL APIs with Eclipse Vert.x — WebSocket Transport (graphql-transport-ws)

This project is the **WebSocket/Subscription transport** variant of the GraphQL APIs. While the [graphql-http](../web/graphql-http) module uses the standard HTTP `GraphQLHandler` with the legacy `graphql-ws` subprotocol, this module uses the dedicated `GraphQLWSHandler` from `io.vertx.ext.web.handler.graphql.ws` which implements the newer **`graphql-transport-ws`** protocol.

The project uses **Vert.x 5.1.3**, **Java 25**, and the **Launcher** approach (`io.vertx.launcher.application.VertxApplication`). It also uses RxJava 3 (`io.reactivex.rxjava3:rxjava`), Lombok, SLF4J/Logback, and Jackson BOM for version alignment.

In this module, **all** GraphQL operations (queries, mutations, and subscriptions) flow through the WebSocket connection, rather than just HTTP POST for queries/mutations and WebSocket only for subscriptions.

Checkout the [complete sample codes from my Github](https://github.com/hantsy/vertx-sandbox/tree/master/graphql-transport-ws).

## Dependencies

Add the following dependency into your *pom.xml*.

```xml
<dependency>
    <groupId>io.vertx</groupId>
    <artifactId>vertx-web-graphql</artifactId>
</dependency>
```

Other key dependencies include:

- `io.reactivex.rxjava3:rxjava` — RxJava 3 (used for ReactiveStreams `Publisher` in subscriptions)
- `vertx-pg-client` — PostgreSQL client (via `Pool`/`PgBuilder`)
- `vertx-launcher-application` — Launcher entry point
- `jackson-bom` — Jackson version alignment (2.22.0)
- Lombok, SLF4J + Logback

The project uses `io.vertx:vertx-stack-depchain:${vertx.version}` (pom, import scope) for dependency management.

## MainVerticle

The entry point extends `VerticleBase` (Vert.x 5 replacement for `AbstractVerticle`) and uses `Future<?> start()`.

```java start=null
@Slf4j
public class MainVerticle extends VerticleBase {

    static {
        var objectMapper = DatabindCodec.mapper();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS);
        objectMapper.disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS);
        JavaTimeModule module = new JavaTimeModule();
        objectMapper.registerModule(module);
    }

    @Override
    public Future<?> start() throws Exception {
        // Create a PgPool using PgBuilder (Vert.x 5 API)
        var pgPool = pgPool();

        // instantiate repos
        var postRepository = new PostRepository(pgPool);
        var commentRepository = new CommentRepository(pgPool);
        var authorRepository = new AuthorRepository(pgPool);

        // Data initialization
        var initializer = new DataInitializer(postRepository, commentRepository, authorRepository);
        initializer.run();

        // assemble services
        var postService = new PostService(postRepository, commentRepository, authorRepository);
        var authorService = new AuthorService(authorRepository);

        // assemble DataLoaders and DataFetchers
        var dataLoaders = new DataLoaders(authorService, postService);
        var dataFetchers = new DataFetchers(postService);

        // setup GraphQL
        GraphQL graphQL = setupGraphQLJava(dataFetchers);

        // Configure routes
        var router = setupRoutes(graphQL, dataLoaders);

        // enable `graphql-transport-ws` Protocol for all GraphQL requests
        HttpServerOptions httpServerOptions = new HttpServerOptions()
            .addWebSocketSubProtocol("graphql-transport-ws");

        return vertx.createHttpServer(httpServerOptions)
            .requestHandler(router)
            .listen(8080)
            .onSuccess(server -> log.info("HTTP server started on port {}", server.actualPort()))
            .onFailure(event -> log.info("Failed to start HTTP server: {}", event.getMessage()));
    }
    // ...
}
```

The key difference from the HTTP variant: the server enables the **`graphql-transport-ws`** subprotocol instead of `graphql-ws`.

## Route Setup with GraphQLWSHandler

The route setup uses the dedicated `GraphQLWSHandler` (builder pattern) instead of `GraphQLHandler`:

```java start=null
private Router setupRoutes(GraphQL graphQL, DataLoaders dataLoaders) {
    Router router = Router.router(vertx);

    // Enable CORS
    router.route().handler(CorsHandler.create().allowedMethod(GET).allowedMethod(POST));
    router.route().handler(BodyHandler.create());

    // Register `graphql-transport-ws` protocol handler
    GraphQLWSOptions options = new GraphQLWSOptions()
        .setConnectionInitWaitTimeout(5000L);

    GraphQLWSHandler graphQLWSHandler = GraphQLWSHandler.builder(graphQL)
        .onConnectionInit(connectionInitEvent -> {
            JsonObject payload = connectionInitEvent.message().message()
                .getJsonObject("payload");
            log.info("connection init event: {}", payload);
            if (payload != null && payload.containsKey("rejectMessage")) {
                connectionInitEvent.fail(payload.getString("rejectMessage"));
                return;
            }
            connectionInitEvent.complete(payload);
        })
        .beforeExecute(event -> event.builder()
            .dataLoaderRegistry(buildDataLoaderRegistry(dataLoaders))
            .build()
        )
        .with(options)
        .build();

    router.route("/graphql").handler(graphQLWSHandler);

    // GraphiQL UI
    GraphiQLHandlerOptions graphiqlOptions = new GraphiQLHandlerOptions().setEnabled(true);
    GraphiQLHandler graphiQLHandler = GraphiQLHandler.create(vertx, graphiqlOptions);
    router.get("/graphiql/*").subRouter(graphiQLHandler.router());

    return router;
}
```

Key differences from the HTTP variant:
- Uses `GraphQLWSHandler.builder(graphQL)` instead of `GraphQLHandler.builder(graphQL)`
- Uses `GraphQLWSOptions` instead of `GraphQLHandlerOptions`
- The handler is registered via `router.route("/graphql")` (handles all HTTP methods for WebSocket upgrade)
- No separate `GraphQLHandler` is registered — all operations flow through the WebSocket
- CORS is enabled via `CorsHandler`
- `connectionInitWaitTimeout` controls how long to wait for the `connection_init` message

## Connection Init Handler

The `onConnectionInit` callback receives a `ConnectionInitEvent` which wraps the WebSocket message. It allows you to inspect the `connectionParams` payload and accept or reject the connection. This is useful for authentication:

```java start=null
.onConnectionInit(connectionInitEvent -> {
    JsonObject payload = connectionInitEvent.message().message()
        .getJsonObject("payload");
    log.info("connection init event: {}", payload);
    if (payload != null && payload.containsKey("rejectMessage")) {
        connectionInitEvent.fail(payload.getString("rejectMessage"));
        return;
    }
    connectionInitEvent.complete(payload);
})
```

## Database Pool (PgBuilder)

Same PgBuilder pattern as the HTTP variant:

```java start=null
private Pool pgPool() {
    PgConnectOptions connectOptions = new PgConnectOptions()
        .setPort(5432)
        .setHost("localhost")
        .setDatabase("blogdb")
        .setUser("user")
        .setPassword("password");

    PoolOptions poolOptions = new PoolOptions().setMaxSize(5);

    return PgBuilder.pool()
        .with(poolOptions)
        .connectingTo(connectOptions)
        .using(vertx)
        .build();
}
```

## GraphQL Schema

The schema is similar to the HTTP variant, but the `upload` mutation is **disabled** because file uploads are not supported over the WebSocket transport protocol:

```gql
directive @uppercase on FIELD_DEFINITION

scalar LocalDateTime
scalar UUID
scalar Upload

type Post {
    id: ID!
    title: String! @uppercase
    content: String
    comments: [Comment]
    status: PostStatus
    createdAt: LocalDateTime
    authorId: String
    author: Author
}

type Author {
    id: ID!
    name: String!
    email: String!
    createdAt: LocalDateTime
    posts: [Post]
}
type Comment {
    id: ID!
    content: String!
    createdAt: LocalDateTime
    postId: String!
}

input CreatePostInput {
    title: String!
    content: String!
}

input CommentInput {
    postId: String!
    content: String!
}

type Query {
    allPosts: [Post!]!
    postById(postId: String!): Post
}

type Mutation {
    createPost(createPostInput: CreatePostInput!): UUID!
    # upload is not available for websocket transport protocol
    #upload(file: Upload!): Boolean
    addComment(commentInput: CommentInput!): UUID!
}

type Subscription{
    commentAdded: Comment!
}

enum PostStatus {
    DRAFT
    PENDING_MODERATION
    PUBLISHED
}
```

## Subscription Data Fetcher (WebSocket Context)

In the transport-ws variant, the subscription data fetcher accesses the WebSocket message via `dfe.getLocalContext()` (returning `io.vertx.ext.web.handler.graphql.ws.Message`) instead of the old `ApolloWSMessage`:

```java start=null
private final ReplaySubject<Comment> subject = ReplaySubject.create(1);

public DataFetcher<Publisher<Comment>> commentAdded() {
    return (DataFetchingEnvironment dfe) -> {
        Message message = dfe.getLocalContext();
        log.info("msg: {}, connectionParams: {}",
            message.message(), message.connectionParams());
        ConnectableObservable<Comment> connectableObservable = subject.share().publish();
        connectableObservable.connect();
        return connectableObservable.toFlowable(BackpressureStrategy.BUFFER);
    };
}
```

The `Message` interface provides `.message()` (the raw JSON message) and `.connectionParams()` (the initial connection parameters).

## Data Fetchers

The mutation `addComment` uses RxJava 3's `ReplaySubject` to publish newly created comments to all connected subscription clients:

```java start=null
public DataFetcher<CompletionStage<UUID>> addComment() {
    return (DataFetchingEnvironment dfe) -> {
        var input = DatabindCodec.mapper().convertValue(
            dfe.getArgument("commentInput"), CommentInput.class);
        return this.posts.addComment(input)
            .onSuccess(id -> this.posts.getCommentById(id.toString())
                .onSuccess(subject::onNext))
            .toCompletionStage();
    };
}
```

## Testing

The test uses `WebSocketClient` to connect via the `graphql-transport-ws` protocol:

```java start=null
@ExtendWith(VertxExtension.class)
@Slf4j
public class TestMainVerticle {
    WebSocketClient client;

    @BeforeEach
    void setup(Vertx vertx, VertxTestContext testContext) {
        vertx.deployVerticle(new MainVerticle())
            .onComplete(testContext.succeeding(id -> {
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
                socket.textMessageHandler(text -> {
                    JsonObject obj = new JsonObject(text);
                    String type = obj.getString("type");
                    if (type.equals("connection_ack")) {
                        return;
                    } else if (type.equals("next")) {
                        var id = obj.getJsonObject("payload")
                            .getJsonObject("data")
                            .getString("createPost");
                        createdPostReplay.add(id);
                    }
                    latch.countDown();
                });

                // Initialize connection
                JsonObject messageInit = new JsonObject()
                    .put("type", "connection_init")
                    .put("id", "1");

                // Subscribe to mutation
                JsonObject createPostData = new JsonObject()
                    .put("payload", new JsonObject()
                        .put("query",
                            "mutation newPost($input:CreatePostInput!){ createPost(createPostInput:$input)}")
                        .put("variables", new JsonObject()
                            .put("input", new JsonObject()
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
            .onFailure(e -> testContext.failNow(e));

        latch.await(5000, TimeUnit.MILLISECONDS);
        assertThat(createdPostReplay).hasSize(1);
        assertThat(createdPostReplay.getFirst()).isNotNull();
    }
}
```

The test uses `WebSocketClient` and sends GraphQL operations as WebSocket frames using the `graphql-transport-ws` protocol (`subscribe` type for mutations/queries, `connection_init` for initialization).

Get the [complete sample codes from my Github](https://github.com/hantsy/vertx-sandbox/tree/master/graphql-transport-ws).
