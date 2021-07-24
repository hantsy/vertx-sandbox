# Building GraphQL APIs with Eclipse Vertx

We have discussed GraphQL in a former [Quarkus GraphQL post](https://itnext.io/building-graphql-apis-with-quarkus-dbbf23f897df). In this post,  we will explore the GraphQL support in Eclipse Vertx.

> Quarkus also includes an alternative GraphQL extension which use the Eclipse Vertx GraphQL feature. 

Follow the steps in the [Building RESTful APIs with Eclipse Vertx](https://itnext.io/building-restful-apis-with-eclipse-vertx-4ce89d8eeb74) and create a new Eclipse Vertx project, do not forget to add *GraphQL* into **Dependencies**.

Or add the following dependency into the existing *pom.xml* file directly.

```xml
 <dependency>
     <groupId>io.vertx</groupId>
     <artifactId>vertx-web-graphql</artifactId>
</dependency>
```

Checkout the [complete sample codes from my Github](https://github.com/hantsy/vertx-sandbox/tree/master/graphql).

Vertx provides a specific `GraphQLHandler` to  handle GraphQL request from client.

Fill the `MainVerticle` with the following content.

```java
@Slf4j
public class MainVerticle extends AbstractVerticle {

    static {
        log.info("Customizing the built-in jackson ObjectMapper...");
        var objectMapper = DatabindCodec.mapper();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS);
        objectMapper.disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS);

        JavaTimeModule module = new JavaTimeModule();
        objectMapper.registerModule(module);
    }

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        log.info("Starting HTTP server...");
        //setupLogging();

        //Create a PgPool instance
        var pgPool = pgPool();

        // instantiate repos
        var postRepository = new PostRepository(pgPool);
        var commentRepository = new CommentRepository(pgPool);
        var authorRepository = new AuthorRepository(pgPool);

        // Initializing the sample data
        var initializer = new DataInitializer(postRepository, commentRepository, authorRepository);
        initializer.run();

        //assemble PostService
        var postService = new PostService(postRepository, commentRepository, authorRepository);
        var authorService = new AuthorService(authorRepository);

        // assemble DataLoaders
        var dataLoaders = new DataLoaders(authorService, postService);

        //assemble DataFetcher
        var dataFetchers = new DataFetchers(postService);

        // setup GraphQL
        GraphQL graphQL = setupGraphQLJava(dataFetchers);

        // Configure routes
        var router = setupRoutes(graphQL, dataLoaders);

        // enable GraphQL Websocket Protocol
        HttpServerOptions httpServerOptions = new HttpServerOptions()
            .addWebSocketSubProtocol("graphql-ws");
        // Create the HTTP server
        vertx.createHttpServer(httpServerOptions)
            // Handle every request using the router
            .requestHandler(router)
            // Start listening
            .listen(8080)
            // Print the port
            .onSuccess(server -> {
                startPromise.complete();
                log.info("HTTP server started on port " + server.actualPort());
            })
            .onFailure(event -> {
                startPromise.fail(event);
                log.info("Failed to start HTTP server:" + event.getMessage());
            })
        ;
    }

    //create routes
    private Router setupRoutes(GraphQL graphQL, DataLoaders dataLoaders) {

        // Create a Router
        Router router = Router.router(vertx);

        // register BodyHandler globally.
        router.route().handler(BodyHandler.create());

        // register GraphQL Subscription websocket handler.
        ApolloWSOptions apolloWSOptions = new ApolloWSOptions();
        router.route("/graphql").handler(
            ApolloWSHandler.create(graphQL, apolloWSOptions)
                .connectionInitHandler(connectionInitEvent -> {
                    JsonObject payload = connectionInitEvent.message().content().getJsonObject("payload");
                    log.info("connection init event: {}", payload);
                    if (payload != null && payload.containsKey("rejectMessage")) {
                        connectionInitEvent.fail(payload.getString("rejectMessage"));
                        return;
                    }
                    connectionInitEvent.complete(payload);
                })
                //.connectionHandler(event -> log.info("connection event: {}", event))
                //.messageHandler(msg -> log.info("websocket message: {}", msg.content().toString()))
                //.endHandler(event -> log.info("end event: {}", event))
        );

        GraphQLHandlerOptions options = new GraphQLHandlerOptions()
            // enable multipart for file upload.
            .setRequestMultipartEnabled(true)
            .setRequestBatchingEnabled(true);
        // register `/graphql` for GraphQL handler
        // alternatively, use `router.route()` to enable GET and POST http methods
        router.post("/graphql")
            .handler(
                GraphQLHandler.create(graphQL, options)
                    .dataLoaderRegistry(buildDataLoaderRegistry(dataLoaders))
                //.locale()
                //.queryContext()
            );

        // register `/graphiql` endpoint for the GraphiQL UI
        GraphiQLHandlerOptions graphiqlOptions = new GraphiQLHandlerOptions()
            .setEnabled(true);
        router.get("/graphiql/*").handler(GraphiQLHandler.create(graphiqlOptions));

        router.get("/hello").handler(rc -> rc.response().end("Hello from my route"));

        return router;
    }

    private Function<RoutingContext, DataLoaderRegistry> buildDataLoaderRegistry(DataLoaders dataLoaders) {
        DataLoaderRegistry registry = new DataLoaderRegistry();
        registry.register("commentsLoader", dataLoaders.commentsLoader());
        registry.register("authorsLoader", dataLoaders.authorsLoader());
        return rc -> registry;
    }

    @SneakyThrows
    private GraphQL setupGraphQLJava(DataFetchers dataFetchers) {
        TypeDefinitionRegistry typeDefinitionRegistry = buildTypeDefinitionRegistry();
        RuntimeWiring runtimeWiring = buildRuntimeWiring(dataFetchers);
        GraphQLSchema graphQLSchema = buildGraphQLSchema(typeDefinitionRegistry, runtimeWiring);
        return buildGraphQL(graphQLSchema);
    }

    private GraphQL buildGraphQL(GraphQLSchema graphQLSchema) {
        return GraphQL.newGraphQL(graphQLSchema)
            .defaultDataFetcherExceptionHandler(new CustomDataFetchingExceptionHandler())
            //.queryExecutionStrategy()
            //.mutationExecutionStrategy()
            //.subscriptionExecutionStrategy()
            //.instrumentation()
            .build();
    }

    private GraphQLSchema buildGraphQLSchema(TypeDefinitionRegistry typeDefinitionRegistry, RuntimeWiring runtimeWiring) {
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);
        return graphQLSchema;
    }

    private TypeDefinitionRegistry buildTypeDefinitionRegistry() throws IOException, URISyntaxException {
        var schema = Files.readString(Paths.get(getClass().getResource("/schema/schema.graphql").toURI()));
        //String schema = vertx.fileSystem().readFileBlocking("/schema/schema.graphql").toString();

        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);
        return typeDefinitionRegistry;
    }

    private RuntimeWiring buildRuntimeWiring(DataFetchers dataFetchers) {
        return newRuntimeWiring()
            // the following codes are moved to CodeRegistry, the central place to configure the execution codes.
            /*
            .wiringFactory(new WiringFactory() {
                @Override
                public DataFetcher<Object> getDefaultDataFetcher(FieldWiringEnvironment environment) {
                    return VertxPropertyDataFetcher.create(environment.getFieldDefinition().getName());
                }
            })
            .type("Query", builder -> builder
                .dataFetcher("postById", dataFetchers.getPostById())
                .dataFetcher("allPosts", dataFetchers.getAllPosts())
            )
            .type("Mutation", builder -> builder.dataFetcher("createPost", dataFetchers.createPost()))
            .type("Post", builder -> builder
                .dataFetcher("author", dataFetchers.authorOfPost())
                .dataFetcher("comments", dataFetchers.commentsOfPost())
            )
            */
            .codeRegistry(buildCodeRegistry(dataFetchers))
            .scalar(Scalars.localDateTimeType())
            .scalar(Scalars.uuidType())
            .scalar(UploadScalar.build())// handling `Upload` scalar
            .directive("uppercase", new UpperCaseDirectiveWiring())
            .build();
    }

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
            //.typeResolver()
            //.fieldVisibility()
            .defaultDataFetcher(environment -> VertxPropertyDataFetcher.create(environment.getFieldDefinition().getName()))
            .build();
    }

    private PgPool pgPool() {
        PgConnectOptions connectOptions = new PgConnectOptions()
            .setPort(5432)
            .setHost("localhost")
            .setDatabase("blogdb")
            .setUser("user")
            .setPassword("password");

        // Pool Options
        PoolOptions poolOptions = new PoolOptions().setMaxSize(5);

        // Create the pool from the data object
        PgPool pool = PgPool.pool(vertx, connectOptions, poolOptions);

        return pool;
    }

}
```

The `start` method is similar to the one  in the previous posts, but here it enabled *graphql-ws* WebSocket sub protocol to activate GraphQL *Subscription* support.

In the *setupRoutes* method, it adds the route */graphql* to use `GraphQLHanlder` to handle the HTTP request and enable WebSocket support via */graphql* endpoint, also adds route */graphiql* to activate GraphiQL interactive Web UI.

As you see, the following is used to create a `GraphQLHandler`  instance.

```java
GraphQLHandler.create(graphQL, options)
                    .dataLoaderRegistry(buildDataLoaderRegistry(dataLoaders))
```

It requires a `GraphQL` instance and optional `options` to initialize a `GraphQL` instance. 

To build a `GraphQL` instance, it requires a `GraphQLSchema` which depends on the following two essential objects:

* A `TypeDefinitionRegistry` to parse the graphql scheme definitions from files, see the above `buildTypeDefinitionRegistry` method.
* A `RuntimeWiring` instance to assemble runtime handler to serve the GraphQL request, see the above `buildRuntimeWiring` method.

In the GraphQLHandler, register the global data loaders which will be instantiated in every request. It is used to decrease the frequency of executing queries in a N+1 query and improve the application performance.

Let's have a look at the graphql schema file under the *main/resources/schema/schema.graphql* folder.

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

In this schema file, we declare 3 top level types:  *Query*, *Mutation* and *Subscription* which presents the basic operations defined in our application. The `Post` and `Comment`  are generic types to present the types used in the returned response.  The `CreatePostInput` and `CommentInput` are input arguments presents the payload of the graphql request. The *scalar* keyword defines custom scalar types. The *directive* defines custom directive *@uppercase* applied on field.

In the `MainVerticle.buildCodeRegistry` method, it assembles data fetchers according to the coordinates of the types defined in the schema definitions.

For example, when performing a *Query*:  `postById`, it will execute the `dataFetchers.postById` defined in the `buildCodeRegistry` method.

```java
.dataFetchers("Query", Map.of(
                "postById", dataFetchers.getPostById(),
    ...
```

Looking into the `buildRuntimeWiring`, it also declars the `Scalar`, `Directive`, etc.

The following is an example of custom Scalar type.

```java
// LocalDateTimeScalar
public class LocalDateTimeScalar implements Coercing<LocalDateTime, String> {
    @Override
    public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
        if (dataFetcherResult instanceof LocalDateTime) {
            return ((LocalDateTime) dataFetcherResult).format(DateTimeFormatter.ISO_DATE_TIME);
        } else {
            throw new CoercingSerializeException("Not a valid DateTime");
        }
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
//Scalars
public class Scalars {

    public static GraphQLScalarType localDateTimeType() {
        return GraphQLScalarType.newScalar()
                .name("LocalDateTime")
                .description("LocalDateTime type")
                .coercing(new LocalDateTimeScalar())
                .build();
    }
}

//register custom scalar type in the MainVertcle buildRuntimeWiring
newRuntimeWiring()
    ...
    .scalar(Scalars.localDateTimeType())
```

Vertx GraphQL provide a `UploadScalar` for uploading files. Check out the source codes and explore the `UUIDScalar` implementation yourself.

Similarly register a custom *Directive* in the `buildRuntimeWiring`, eg. the `@uppercase` directive.

```java
//UpperCaseDirectiveWiring
public class UpperCaseDirectiveWiring implements SchemaDirectiveWiring {
    @Override
    public GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> env) {

        var field = env.getElement();
        var parentType = env.getFieldsContainer();

        var originalDataFetcher = env.getCodeRegistry().getDataFetcher(parentType, field);
        var dataFetcher = DataFetcherFactories.wrapDataFetcher(originalDataFetcher,
                (dataFetchingEnvironment, value) -> {
                    if (value instanceof String s) {
                        return s.toUpperCase();
                    }
                    return value;
                }
        );

        env.getCodeRegistry().dataFetcher(parentType, field, dataFetcher);
        return field;
    }
}
//register custom scalar directive in the MainVertcle buildRuntimeWiring
newRuntimeWiring()
    ...
    .directive("uppercase", new UpperCaseDirectiveWiring())
```

Let's move on to the data fetchers which are responsible for resolving the type values at runtime.

For example, in the GraphiQL UI page, try to send a predefined query like this.

```gql
query {
    allPosts{
        id
        title
        content
        author{ name }
        comments{ content }
    }
}
```

It means, it will perform an `allPosts` *Query*, and returns a Post array, each item includes fields `id`, `title` and `content` and an `author` object with an exact `name` field, and a `comments` array each item includes a single `content` field.

When the GraphQL request is sent, `GraphQLHandler` will handle it.

* Validate the GraphQL request and ensure it follows schema type definitions.
* Locate the data fetchers via type coordinates, *Query* and *allPosts*. 
* Assemble the returned values according to the request format.
* When resolving `author` field, it will try to locate *Post* and *author* to find the existing data fetcher (`dataFetchers.authorOfPost()`) to handle it. Similarly it handles *comments* via `dataFetchers.commentsOfPost()`.
* Other generic fields, use the default data fetcher to return the value of the corresponding field of this Post directly.

The `Mutation` handling is similar to `Query`, but it is designed for performing some mutations. For example, use Mutation `createPost` to create a new post, it accepts a `CreatePostInput` input argument, then delegate to `dataFecthers.createPost` to handle it.

The file uploading is defined by [GraphQL multipart request specification](https://github.com/jaydenseric/graphql-multipart-request-spec), not part of the standard GraphQL spec. 

The following is the data fetcher to handle the file uploading.

```java
public DataFetcher<Boolean> upload() {
    return (DataFetchingEnvironment dfe) -> {

        FileUpload upload = dfe.getArgument("file");
        log.info("name: {}", upload.name());
        log.info("file name: {}", upload.fileName());
        log.info("uploaded file name: {}", upload.uploadedFileName());
        log.info("content type: {}", upload.contentType());
        log.info("charset: {}", upload.charSet());
        log.info("size: {}", upload.size());
        //            String fileContent = Vertx.vertx().fileSystem().readFileBlocking(upload.uploadedFileName()).toString();
        //            log.info("file content: {}", fileContent);
        return true;
    };
}
```

Vertx creates a temporary file for the uploaded file, it is easy to read the files from local file system.

For the `Subscription`, it is used for tracking the updates from the backend, such as stock trade news, notification, etc. GraphQL Java requires that it has to return a  ReactiveStreams `Publisher` type.

The following is an example of sending notification when a comment is added.

```java
public VertxDataFetcher<UUID> addComment() {
    return VertxDataFetcher.create((DataFetchingEnvironment dfe) -> {
        var commentInputArg = dfe.getArgument("commentInput");
        var jacksonMapper = DatabindCodec.mapper();
        var input = jacksonMapper.convertValue(commentInputArg, CommentInput.class);
        return this.posts.addComment(input)
            .onSuccess(id -> this.posts.getCommentById(id.toString())
                       .onSuccess(c -> subject.onNext(c)));
    });
}

private ReplaySubject<Comment> subject = ReplaySubject.create(1);

public DataFetcher<Publisher<Comment>> commentAdded() {
    return (DataFetchingEnvironment dfe) -> {
        ApolloWSMessage message = dfe.getContext();
        log.info("msg: {}, connectionParams: {}", message.content(), message.connectionParams());
        ConnectableObservable<Comment> connectableObservable = subject.share().publish();
        connectableObservable.connect();
        log.info("connect to `commentAdded`");
        return connectableObservable.toFlowable(BackpressureStrategy.BUFFER);
    };
}
```

The above example uses RxJava 3's `ReplaySubject` as a processor  to emit the message to the connected clients. We have configured in `MainVerticle` to use WebSocket protocol to handle Subscription. In next post, we will create a WebSocket client to consume this message endpoints.

Here we skip other codes, which are similar to the former Quarkus GraphQL post, check out the [complete sample codes from my Github](https://github.com/hantsy/vertx-sandbox/tree/master/graphql) and explore it yourself.

Not like Quarkus, Eclipse Vertx does not provides a specific GraphQL client to simplify the GraphQL request in Java. 

To write tests for the GraphQL APIs, you have to switch to use the generic Vertx Http Client. And you have to know well about [GraphQL over HTTP specification](https://graphql.org/learn/serving-over-http/).

The following is an example of testing *allPosts* query and *createPost* mutation using Vertx HttpClient.

```java
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
                    author{ name }
                    comments{ content }
                }
            }""";
        client.request(HttpMethod.POST, "/graphql")
            .flatMap(req -> req.putHeader("Content-Type", "application/json")
                .putHeader("Accept", "application/json")
                .send(Json.encode(Map.of("query", query)))//have to use Json.encode to convert objects to json string.
                .flatMap(HttpClientResponse::body)
            )
            .onComplete(
                testContext.succeeding(
                    buffer -> testContext.verify(
                        () -> {
                            log.info("buf: {}", buffer.toString());
                            JsonArray array = buffer.toJsonObject()
                                .getJsonObject("data")
                                .getJsonArray("allPosts");
                            assertThat(array.size()).isGreaterThan(0);

                            var titles = array.getList().stream().map(o -> ((Map<String, Object>) o).get("title")).toList();
                            assertThat(titles).allMatch(s -> ((String) s).startsWith("DGS POST"));
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
```

 Get the [ sample codes from my Github](https://github.com/hantsy/vertx-sandbox/tree/master/graphql).
