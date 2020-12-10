package com.example.demo;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise) throws Exception {

        var pgPool = pgPool();

        var postRepository = PostRepository.create(pgPool);

        var postHandlers = PostsHandler.create(postRepository);

        var intializer = DataInitializer.create(pgPool);
        intializer.initialize();


        var router = routes(postHandlers);

        // Create the HTTP server
        vertx.createHttpServer()
            // Handle every request using the router
            .requestHandler(router)
            // Start listening
            .listen(8888)
            // Print the port
            .onSuccess(server ->
                System.out.println(
                    "HTTP server started on port " + server.actualPort()
                )
            );
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
