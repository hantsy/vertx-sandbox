package com.example.demo;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.springframework.stereotype.Component;

import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class MainVerticle extends VerticleBase {
    private static final Logger LOGGER = Logger.getLogger(MainVerticle.class.getName());


    private final PostsHandler postHandlers;

    public MainVerticle(PostsHandler postHandlers) {
        this.postHandlers = postHandlers;
    }

    @Override
    public Future<?> start() throws Exception {
        LOGGER.log(Level.INFO, "Starting HTTP server...");
        //setupLogging();

        //Create a PgPool instance
        //var pgPool = pgPool();

        //Creating PostRepository
        //var postRepository = PostRepository.create(pgPool);

        //Creating PostHandler
        //var postHandlers = PostsHandler.create(postRepository);

        // Initializing the sample data
//        var initializer = DataInitializer.create(pgPool);
//        initializer.run();

        // Configure routes
        var router = routes(postHandlers);

        // Create the HTTP server
        return vertx.createHttpServer()
            // Handle every request using the router
            .requestHandler(router)
            // Start listening
            .listen(8888)
            // Print the port
            .onSuccess(server -> {
                LOGGER.log(Level.INFO, "HTTP server started on port " + server.actualPort());
            })
            .onFailure(event -> {
                LOGGER.log(Level.SEVERE, "Failed to start HTTP server:" + event.getMessage());
            })
            ;
    }

    @Override
    public Future<?> stop() throws Exception {
        LOGGER.log(Level.INFO, "Stopping HTTP server...");
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
        router.get("/posts/:id").produces("application/json").handler(handlers::get).failureHandler(frc -> frc.response().setStatusCode(404).end());
        router.put("/posts/:id").consumes("application/json").handler(BodyHandler.create()).handler(handlers::update);
        router.delete("/posts/:id").handler(handlers::delete);

        router.get("/hello").handler(rc -> rc.response().end("Hello from my route"));

        return router;
    }

}
