package com.example.demo;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class MainVerticle extends AbstractVerticle {

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
    public void start(Promise<Void> startPromise) throws Exception {
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
        vertx.createHttpServer()
            // Handle every request using the router
            .requestHandler(router)
            // Start listening
            .listen(8888)
            // Print the port
            .onSuccess(server -> {
                startPromise.complete();
                System.out.println("HTTP server started on port " + server.actualPort());
            })
            .onFailure(event -> {
                startPromise.fail(event);
                System.out.println("Failed to start HTTP server:" + event.getMessage());
            })
        ;
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

        router.get("/hello").handler(rc -> rc.response().end("Hello from my route"));

        return router;
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
        Pool pool = Pool.pool(vertx, connectOptions, poolOptions);

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
