package com.example.demo;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.vertx.core.AbstractVerticle;
import io.vertx.core.VerticleBase;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.mutiny.ext.web.Router;
import io.vertx.mutiny.ext.web.handler.BodyHandler;
import org.springframework.stereotype.Component;

import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class MainVerticle extends AbstractVerticle {
    private static final Logger LOGGER = Logger.getLogger(MainVerticle.class.getName());

    static {
        LOGGER.info("Customizing the built-in jackson ObjectMapper...");
        var objectMapper = DatabindCodec.mapper();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS);
        objectMapper.disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS);

        JavaTimeModule module = new JavaTimeModule();
        objectMapper.registerModule(module);
    }

    private final PostsHandler postHandlers;

    public MainVerticle(PostsHandler postHandlers) {
        this.postHandlers = postHandlers;
    }

    @Override
    public Uni<Void> asyncStart() {
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
            .onItem().invoke(() -> LOGGER.info("Http server is listening on http://127.0.0.1:8888"))
            .onFailure().invoke(Throwable::printStackTrace)
            .replaceWithVoid();
    }

    //create routes
    private Router routes(PostsHandler handlers) {

        // Create a Router
        Router router = Router.router(vertx);
        // register BodyHandler globally.
        //router.route().handler(BodyHandler.create());
        router.get("/posts").produces("application/json")
            .handler(handlers::all);
        router.post("/posts").consumes("application/json")
            .handler(BodyHandler.create())
            .handler(handlers::save);
        router.get("/posts/:id").produces("application/json")
            .handler(handlers::get)
            .failureHandler(frc -> frc.response().setStatusCode(404).endAndAwait());
        router.put("/posts/:id").consumes("application/json")
            .handler(BodyHandler.create())
            .handler(handlers::update);
        router.delete("/posts/:id")
            .handler(handlers::delete);

        // Alternatively, use a respond to receive a function using RoutingContext as input arguments.
        // see: https://github.com/vertx-howtos/hibernate-reactive-howto/blob/master/src/main/java/io/vertx/howtos/hr/MainVerticle.java
        /*router.get("/posts").produces("application/json")
            .respond(handlers::all);
        router.post("/posts").consumes("application/json")
            .handler(BodyHandler.create())
            .respond(handlers::save);
        router.get("/posts/:id").produces("application/json")
            .respond(handlers::get)
            .failureHandler(frc -> frc.response().setStatusCode(404).end());
        router.put("/posts/:id").consumes("application/json")
            .handler(BodyHandler.create())
            .respond(handlers::update);
        router.delete("/posts/:id")
            .respond(handlers::delete);*/

        router.get("/hello").handler(rc -> rc.response().end("Hello from my route"));

        return router;
    }

}
