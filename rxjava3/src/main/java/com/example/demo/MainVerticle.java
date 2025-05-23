package com.example.demo;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.reactivex.rxjava3.core.Completable;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.rxjava3.core.AbstractVerticle;
import io.vertx.rxjava3.ext.web.Router;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import io.vertx.rxjava3.pgclient.PgBuilder;
import io.vertx.rxjava3.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(MainVerticle.class);

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
    public Completable rxStart() {
        log.info("Starting HTTP server...");

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

        // Create the HTTP server and return the future
        return vertx.createHttpServer()
            .requestHandler(router)
            .rxListen(8888)
            .doOnSuccess(server -> {
                log.info("HTTP server started on port " + server.actualPort());
            })
            .doOnError(throwable -> {
                log.error("Failed to start HTTP server:" + throwable.getMessage());
            })
            .ignoreElement();
    }

    @Override
    public Completable rxStop() {
        log.info("Stopping HTTP server...");
        return super.rxStop();
    }

    //create routes
    private Router routes(PostsHandler handlers) {

        // Create a Router
        Router router = Router.router(vertx);
        // register BodyHandler globally.
        //router.route().handler(BodyHandler.create());
        router.get("/posts").produces("application/json").handler(handlers::all);

//        SchemaRouter schemaRouter = SchemaRouter.create(vertx, new SchemaRouterOptions());
//        SchemaParser schemaParser = SchemaParser.createDraft201909SchemaParser(schemaRouter);
//
//        ObjectSchemaBuilder bodySchemaBuilder = objectSchema()
//            .requiredProperty("title", stringSchema().with(minLength(5)).with(maxLength(100)))
//            .requiredProperty("content", stringSchema().with(minLength(10)).with(maxLength(2000)));
//
//        ValidationHandler validation = ValidationHandler
//            .builder(schemaParser)
//            //.queryParameter(param("parameterName", intSchema()))
//            //.pathParameter(param("pathParam", numberSchema()))
//            .body(Bodies.json(bodySchemaBuilder))
//            //.body(Bodies.formUrlEncoded(bodySchemaBuilder))
//            .predicate(RequestPredicate.BODY_REQUIRED)
//            .build();
//
//        Handler<RoutingContext> validationFailureHandler = (RoutingContext rc) -> {
//            if (rc.failure() instanceof BodyProcessorException exception) {
//                rc.response()
//                    .setStatusCode(400)
//                    .end("validation failed.");
//                //.end(exception.toJson().encode());
//            }
//        };

        router.post("/posts").consumes("application/json")
            .handler(BodyHandler.create())
            .handler(handlers::save);

        router.get("/posts/:id").produces("application/json")
            .handler(handlers::get)
            .failureHandler(frc -> frc.response().setStatusCode(404).end());

        router.put("/posts/:id")
            .consumes("application/json")
            .handler(BodyHandler.create())
            .handler(handlers::update);

        router.delete("/posts/:id")
            .handler(handlers::delete);

        router.get("/hello").handler(rc -> rc.response().rxWrite("Hello from my route"));

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
        return PgBuilder.pool()
            .with(poolOptions)
            .connectingTo(connectOptions)
            .using(vertx)
            .build();
    }

}
