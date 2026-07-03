# Building GraphQL APIs with Eclipse Vert.x — HTTP Transport

This project demonstrates building GraphQL APIs with Eclipse Vert.x using the standard HTTP transport. It covers schema definitions, custom scalars, directives, data fetchers, data loaders for N+1 query prevention, file uploads, and GraphQL Subscriptions over the `graphql-ws` WebSocket protocol.

The project uses **Vert.x 5.1.3**, **Java 25**, and the **Launcher** approach (`io.vertx.launcher.application.VertxApplication`). It also uses RxJava 3 (`vertx-rx-java3`), Lombok, SLF4J/Logback, and Jackson BOM for version alignment.

There is also a [WebSocket/Subscription-transport variant](../web/graphql-transport-ws) that uses the `graphql-transport-ws` protocol instead.

Checkout the [complete sample codes from my Github](https://github.com/hantsy/vertx-sandbox/tree/master/graphql-http).

## Dependencies

Add the following dependency into your *pom.xml*.

```xml
<dependency>
    <groupId>io.vertx</groupId>
    <artifactId>vertx-web-graphql</artifactId>
</dependency>
```

Other key dependencies include:

- `vertx-rx-java3` — RxJava 3 bindings (used for ReactiveStreams `Publisher` in subscriptions)
- `vertx-pg-client` — PostgreSQL client (via `Pool`/`PgBuilder`)
- `vertx-launcher-application` — Launcher entry point
- `jackson-bom` — Jackson version alignment (2.22.0)
- Lombok, SLF4J + Logback

The project uses `io.vertx:vertx-stack-depchain:${vertx.version}` (pom, import scope) for dependency management.

## MainVerticle

Vert.x provides `GraphQLHandler` to handle GraphQL requests from clients. The entry point extends `VerticleBase` (the Vert.x 5 replacement for `AbstractVerticle`) and uses `Future<?> start()` instead of the old `start(Promise<Void>)`.

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

        // Initializing the sample data
        var initializer = new DataInitializer(pgPool);
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

        // enable graphql-ws WebSocket sub protocol
        HttpServerOptions httpServerOptions = new HttpServerOptions()
            .addWebSocketSubProtocol("graphql-ws");

        return vertx.createHttpServer(httpServerOptions)
            .requestHandler(router)
            .listen(8080)
            .onSuccess(server -> log.info("HTTP server started on port {}", server.actualPort()))
            .onFailure(event -> log.info("Failed to start HTTP server: {}", event.getMessage()));
    }
    // ...
}
```

The `start` method returns `Future<?>` directly. The HTTP server options enable the `graphql-ws` subprotocol to support GraphQL Subscriptions over WebSocket.

## Route Setup

The `GraphQLHandler` is now built using the **builder** pattern (Vert.x 5). It accepts a `beforeExecute` callback to inject per-request `DataLoaderRegistry` for batching and caching.

```java start=null
private Router setupRoutes(GraphQL graphQL, DataLoaders dataLoaders) {
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());

    GraphQLHandlerOptions options = new GraphQLHandlerOptions()
        .setRequestMultipartEnabled(true)
        .setRequestBatchingEnabled(true);

    GraphQLHandler graphQLHandler = GraphQLHandler.builder(graphQL)
        .with(options)
        .beforeExecute(e ->
            e.builder()
                .dataLoaderRegistry(buildDataLoaderRegistry(dataLoaders).apply(e.context()))
                .build()
        )
        .build();
    router.post("/graphql").handler(graphQLHandler);

    // GraphiQL interactive UI
    GraphiQLHandlerOptions graphiqlOptions = new GraphiQLHandlerOptions().setEnabled(true);
    GraphiQLHandler graphiQLHandler = GraphiQLHandler.create(vertx, graphiqlOptions);
    router.get("/graphiql/*").subRouter(graphiQLHandler.router());

    return router;
}
```

Note that `GraphiQLHandler.create(vertx, graphiqlOptions)` uses the overload that accepts a `Vertx` instance (Vert.x 5 API).

## Database Pool (PgBuilder)

In Vert.x 5, the recommended way to create a `PgPool` is via `PgBuilder` instead of the static `PgPool.pool()` method.

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

The return type is `Pool` (the `io.vertx.sqlclient.Pool` interface) rather than `PgPool` directly.

## GraphQL Schema

The schema is defined in `src/main/resources/schema/schema.graphql`:

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
    upload(file: Upload!): Boolean
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

This declares three top-level operation types: **Query**, **Mutation**, and **Subscription**. The schema uses custom scalars (`LocalDateTime`, `UUID`, `Upload`), a custom directive (`@uppercase`), and input types for mutations.

## Code Registry (Data Fetchers)

Instead of wiring data fetchers inside `RuntimeWiring.type()`, the project uses `GraphQLCodeRegistry` to map data fetchers by type coordinates:

```java start=null
private GraphQLCodeRegistry buildCodeRegistry(DataFetchers dataFetchers) {
    return GraphQLCodeRegistry.newCodeRegistry()
        .dataFetchers("Query", Map.of(
            "postById", dataFetchers.getPostById(),
            "allPosts", dataFetchers.getAllPosts()
        ))
        .dataFetchers("Mutation", Map.of(
            "createPost", dataFetchers.createPost(),
            "upload", dataFetchers.upload(),
            "addComment", dataFetchers.addComment()
        ))
        .dataFetchers("Subscription", Map.of(
            "commentAdded", dataFetchers.commentAdded()
        ))
        .dataFetchers("Post", Map.of(
            "author", dataFetchers.authorOfPost(),
            "comments", dataFetchers.commentsOfPost()
        ))
        .defaultDataFetcher(environment -> PropertyDataFetcher.fetching(environment.getFieldDefinition().getName()))
        .build();
}
```

The `defaultDataFetcher` uses `PropertyDataFetcher.fetching()` which reflects on the source object to get the field value — similar to the old `VertxPropertyDataFetcher` approach.

## Custom Scalars

Custom scalars implement `Coercing<JavaType, String>`:

```java start=null
public class LocalDateTimeScalar implements Coercing<LocalDateTime, String> {
    @Override
    public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
        if (dataFetcherResult instanceof LocalDateTime) {
            return ((LocalDateTime) dataFetcherResult).format(DateTimeFormatter.ISO_DATE_TIME);
        }
        throw new CoercingSerializeException("Not a valid DateTime");
    }

    @Override
    public LocalDateTime parseValue(Object input) throws CoercingParseValueException {
        return LocalDateTime.parse(input.toString(), DateTimeFormatter.ISO_DATE_TIME);
    }

    @Override
    public LocalDateTime parseLiteral(Object input) throws CoercingParseLiteralException {
        if (input instanceof StringValue) {
            return LocalDateTime.parse(((StringValue) input).getValue(), DateTimeFormatter.ISO_DATE_TIME);
        }
        throw new CoercingParseLiteralException("Value is not a valid ISO date time");
    }
}
```

Scalar types are registered in the `RuntimeWiring`:

```java start=null
.scalar(Scalars.localDateTimeType())
.scalar(Scalars.uuidType())
.scalar(UploadScalar.build())
```

Vert.x GraphQL provides a built-in `UploadScalar` for file uploads.

## Custom Directives

The `@uppercase` directive uses the newer `env.setFieldDataFetcher()` / `env.getFieldDataFetcher()` API (instead of looking up via `env.getCodeRegistry()`):

```java start=null
public class UpperCaseDirectiveWiring implements SchemaDirectiveWiring {
    @Override
    public GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> env) {
        var field = env.getElement();
        var dataFetcher = DataFetcherFactories.wrapDataFetcher(env.getFieldDataFetcher(),
            (dataFetchingEnvironment, value) -> {
                if (value instanceof String s) {
                    return s.toUpperCase();
                }
                return value;
            }
        );
        env.setFieldDataFetcher(dataFetcher);
        return field;
    }
}
```

## Data Fetchers and Subscriptions

Data fetchers return `CompletionStage` by delegating to the service layer's `Future` via `.toCompletionStage()`. For subscriptions, RxJava 3's `ReplaySubject` acts as an event bus.

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

private final ReplaySubject<Comment> subject = ReplaySubject.create(1);

public DataFetcher<Publisher<Comment>> commentAdded() {
    return (DataFetchingEnvironment dfe) -> {
        ConnectableObservable<Comment> connectableObservable = subject.share().publish();
        connectableObservable.connect();
        return connectableObservable.toFlowable(BackpressureStrategy.BUFFER);
    };
}
```

The subscription data fetcher returns a ReactiveStreams `Publisher` from RxJava 3's `Flowable`.

## Testing

Tests use `@ExtendWith(VertxExtension.class)` (JUnit 5 + Vert.x) with `vertx-junit5`.

```java start=null
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
                    var titles = array.getList().stream()
                        .map(o -> ((Map<String, Object>) o).get("title")).toList();
                    assertThat(titles).allMatch(s -> ((String) s).startsWith("HELLO"));
                    testContext.completeNow();
                })
            ));
    }

    @Test
    void createPost(Vertx vertx, VertxTestContext testContext) {
        String TITLE = "My post created by Vertx HttpClient";
        var creatPostQuery = """
            mutation newPost($input:CreatePostInput!){
                createPost(createPostInput:$input)
            }""";
        client.request(HttpMethod.POST, "/graphql")
            .flatMap(req -> req.putHeader("Content-Type", "application/json")
                .putHeader("Accept", "application/json")
                .send(Json.encode(Map.of(
                    "query", creatPostQuery,
                    "variables", Map.of("input", Map.of("title", TITLE, "content", "content of my post"))
                )))
                .flatMap(HttpClientResponse::body)
            )
            // ... chained to get post by ID and verify the @uppercase directive
            .onComplete(testContext.succeeding(buffer ->
                testContext.verify(() -> {
                    String title = buffer.toJsonObject()
                        .getJsonObject("data")
                        .getJsonObject("postById")
                        .getString("title");
                    assertThat(title).isEqualTo(TITLE.toUpperCase());
                    testContext.completeNow();
                })
            ));
    }
}
```

Note that `vertx.deployVerticle(new MainVerticle())` now returns a `Future` directly (Vert.x 5 API change), and uses the fluent `onComplete(testContext.succeeding(...))` pattern.

Get the [complete sample codes from my Github](https://github.com/hantsy/vertx-sandbox/tree/master/graphql-http).
