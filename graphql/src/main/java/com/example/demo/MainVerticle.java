package com.example.demo;

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
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.*;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.graphql.*;
import io.vertx.ext.web.handler.graphql.schema.VertxPropertyDataFetcher;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.dataloader.DataLoaderRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.LogManager;

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring;

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

        // register `/graphql` handler
        GraphQLHandlerOptions options = new GraphQLHandlerOptions()
            // enable multipart for file upload.
            .setRequestMultipartEnabled(true)
            .setRequestBatchingEnabled(true);

        router.route("/subscriptions").handler(ApolloWSHandler.create(graphQL));

        // alternatively, use `router.route()` to enable GET and POST http methods
        router.post("/graphql")
            .handler(
                GraphQLHandler.create(graphQL, options)
                    .dataLoaderRegistry(
                        rc -> {
                            DataLoaderRegistry registry = new DataLoaderRegistry();
                            registry.register("commentsLoader", dataLoaders.commentsLoader());
                            registry.register("authorsLoader", dataLoaders.authorsLoader());

                            return registry;
                        }
                    )

            );

        // register `/graphiql` endpoint for the GraphiQL UI
        GraphiQLHandlerOptions graphiqlOptions = new GraphiQLHandlerOptions()
            .setEnabled(true);
        router.route("/graphiql").handler(GraphiQLHandler.create(graphiqlOptions));

        router.get("/hello").handler(rc -> rc.response().end("Hello from my route"));

        return router;
    }

    @SneakyThrows
    private GraphQL setupGraphQLJava(DataFetchers dataFetchers) {
        var schema = Files.readString(Paths.get(getClass().getResource("/schema/schema.graphql").toURI()));
        //String schema = vertx.fileSystem().readFileBlocking("/schema/schema.graphql").toString();

        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeDefinitionRegistry = schemaParser.parse(schema);

        RuntimeWiring runtimeWiring = newRuntimeWiring()
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
            .scalar(Scalars.localDateTimeType())
            .scalar(Scalars.uuidType())
            .directive("uppercase", new UpperCaseDirectiveWiring())
            .build();

        SchemaGenerator schemaGenerator = new SchemaGenerator();
        GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);

        return GraphQL.newGraphQL(graphQLSchema).build();
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
