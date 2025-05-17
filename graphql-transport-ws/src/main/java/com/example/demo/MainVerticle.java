package com.example.demo;

import com.example.demo.gql.CustomDataFetchingExceptionHandler;
import com.example.demo.gql.DataFetchers;
import com.example.demo.gql.DataLoaders;
import com.example.demo.gql.directives.UpperCaseDirectiveWiring;
import com.example.demo.gql.scalars.Scalars;
import com.example.demo.repository.AuthorRepository;
import com.example.demo.repository.CommentRepository;
import com.example.demo.repository.PostRepository;
import com.example.demo.service.AuthorService;
import com.example.demo.service.PostService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import graphql.GraphQL;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLSchema;
import graphql.schema.PropertyDataFetcher;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.graphql.GraphiQLHandler;
import io.vertx.ext.web.handler.graphql.GraphiQLHandlerOptions;
import io.vertx.ext.web.handler.graphql.UploadScalar;
import io.vertx.ext.web.handler.graphql.ws.GraphQLWSHandler;
import io.vertx.ext.web.handler.graphql.ws.GraphQLWSOptions;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.dataloader.DataLoaderRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.logging.LogManager;

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring;
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.POST;

@Slf4j
public class MainVerticle extends VerticleBase {

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
    public Future<?> start() throws Exception {
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

        // enable `graphql-transport-ws` Protocol for all GraphQL requests.
        HttpServerOptions httpServerOptions = new HttpServerOptions()
                .addWebSocketSubProtocol("graphql-transport-ws");
        // Create the HTTP server
        return vertx.createHttpServer(httpServerOptions)
                // Handle every request using the router
                .requestHandler(router)
                // Start listening
                .listen(8080)
                // Print the port
                .onSuccess(server -> log.info("HTTP server started on port {}", server.actualPort()))
                .onFailure(event -> log.info("Failed to start HTTP server: {}", event.getMessage()));
    }

    //create routes
    private Router setupRoutes(GraphQL graphQL, DataLoaders dataLoaders) {

        // Create a Router
        Router router = Router.router(vertx);

        //enable cors
        router.route().handler(CorsHandler.create().allowedMethod(GET).allowedMethod(POST));

        // register BodyHandler globally.
        router.route().handler(BodyHandler.create());

        // register `transport-ws` protocol handler
        GraphQLWSOptions apolloWSOptions = new GraphQLWSOptions().setConnectionInitWaitTimeout(5000L);
        GraphQLWSHandler graphQLWSHandler = GraphQLWSHandler.builder(graphQL)
                .onConnectionInit(connectionInitEvent -> {
                    JsonObject payload = connectionInitEvent.message().message().getJsonObject("payload");
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
                .with(apolloWSOptions)
                .build();

        router.route("/graphql").handler(graphQLWSHandler);

        // register `/graphiql` endpoint for the GraphiQL UI
        GraphiQLHandlerOptions graphiqlOptions = new GraphiQLHandlerOptions().setEnabled(true);
        GraphiQLHandler graphiQLHandler = GraphiQLHandler.create(vertx, graphiqlOptions);
        router.get("/graphiql/*").subRouter(graphiQLHandler.router());

        router.get("/hello").handler(rc -> rc.response().end("Hello from my route"));

        return router;
    }

    private DataLoaderRegistry buildDataLoaderRegistry(DataLoaders dataLoaders) {
        DataLoaderRegistry registry = new DataLoaderRegistry();
        registry.register("commentsLoader", dataLoaders.commentsLoader());
        registry.register("authorsLoader", dataLoaders.authorsLoader());
        return registry;
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
                        //"upload", dataFetchers.upload(),
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
                .defaultDataFetcher(environment -> PropertyDataFetcher.fetching(environment.getFieldDefinition().getName()))
                .build();
    }

    private Pool pgPool() {
        PgConnectOptions connectOptions = new PgConnectOptions()
                .setPort(5432)
                .setHost("localhost")
                .setDatabase("blogdb")
                .setUser("user")
                .setPassword("password");

        // Pool Options
        PoolOptions poolOptions = new PoolOptions().setMaxSize(5);

        // Create the pool from the data object
        return PgBuilder.pool()
                .with(poolOptions)
                .connectingTo(connectOptions)
                .using(vertx)
                .build();
    }

    /**
     * Configure logging from logging.properties file.
     * When using custom JUL logging properties, named it to vertx-default-jul-logging.properties
     * or set java.util.logging.config.file system property to locate the properties file.
     */
    private static void setupLogging() throws IOException {
        try (InputStream is = MainVerticle.class.getResourceAsStream("/logging.properties")) {
            LogManager.getLogManager().readConfiguration(is);
        }
    }

}
