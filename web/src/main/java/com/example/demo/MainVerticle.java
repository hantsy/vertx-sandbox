package com.example.demo;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.VerticleBase;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class MainVerticle extends VerticleBase {

    private final static Logger LOGGER = Logger.getLogger(MainVerticle.class.getName());

    static {
        LOGGER.info("Customizing the built-in jackson ObjectMapper...");
        var objectMapper = DatabindCodec.mapper();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS);
        objectMapper.disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS);

        JavaTimeModule module = new JavaTimeModule();
        objectMapper.registerModule(module);
    }

    @Override
    public Future<?> start() throws Exception {
        LOGGER.log(Level.INFO, "Starting HTTP server...");
        //setupLogging();

        //Create a PgPool instance
        var pgPool = pgPool();

        //Creating PostRepository
        var postRepository = PostRepository.create(pgPool);

        //Creating PostHandler
        var postHandlers = PostsHandler.create(postRepository);

        // Initializing the sample data
        var initializer = DataInitializer.create(pgPool);
        initializer.run();

        // Configure routes
        var router = routes(postHandlers);

        // Create the HTTP server
        return vertx.createHttpServer()
            // Handle every request using the router
            .requestHandler(router)
            // Start listening
            .listen(8888)
            // Print the port
            .onSuccess(server -> LOGGER.log(Level.INFO, "HTTP server started on port:" + server.actualPort()))
            .onFailure(event -> LOGGER.log(Level.INFO, "Failed to start HTTP server:" + event.getMessage()));
    }

    @Override
    public Future<?> stop() throws Exception {
        return super.stop();
    }

    //create routes
    private Router routes(PostsHandler handlers) {

        // Create a Router
        Router router = Router.router(vertx);
        // register BodyHandler globally.
        //router.route().handler(BodyHandler.create());
        router.get("/posts").produces("application/json").handler(handlers::all);
        router.post("/posts").consumes("application/json").handler(BodyHandler.create()).handler(handlers::save);
        router.get("/posts/:id").produces("application/json").handler(handlers::get)
            .failureHandler(frc -> {
                Throwable failure = frc.failure();
                if (failure instanceof PostNotFoundException) {
                    frc.response().setStatusCode(404).end();
                }
                frc.response().setStatusCode(500).setStatusMessage("Server internal error:" + failure.getMessage()).end();
            });
        router.put("/posts/:id").consumes("application/json").handler(BodyHandler.create()).handler(handlers::update);
        router.delete("/posts/:id").handler(handlers::delete);

        // Mount the handler for all incoming requests at every path and HTTP method
        router.get("/hello").handler(context -> {
            // Get the address of the request
            String address = context.request().connection().remoteAddress().toString();
            // Get the query parameter "name"
            MultiMap queryParams = context.queryParams();
            String name = queryParams.contains("name") ? queryParams.get("name") : "unknown";
            // Write a json response
            context.json(
                new JsonObject()
                    .put("name", name)
                    .put("address", address)
                    .put("message", "Hello " + name + " connected from " + address)
            );
        });
        return router;
    }

    private Pool pgPool() {
        PgConnectOptions connectOptions = new PgConnectOptions()
            .setPort(5432)
            .setHost("localhost")
            .setDatabase("blogdb")
            .setUser("user")
            .setPassword("password");
//            .setSslOptions(new ClientSSLOptions()
//                .setTrustOptions(new PemTrustOptions().addCertPath(pathToCert))
//            );

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
     * When using custom JUL logging properties, named it to vertx-default-jul-vertx-default-jul-logging.properties
     * or set java.util.logging.config.file system property to locate the properties file.
     *
     * The method load logging config manually.
     */
    private static void setupLogging() throws IOException {
        try (InputStream is = MainVerticle.class.getResourceAsStream("/logging.properties")) {
            LogManager.getLogManager().readConfiguration(is);
        }
    }

}
