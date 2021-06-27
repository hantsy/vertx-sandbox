package com.example.demo;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import io.reactivex.rxjava3.core.Completable;
import io.vertx.core.Handler;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.ext.web.validation.BadRequestException;
import io.vertx.ext.web.validation.builder.Bodies;
import io.vertx.json.schema.SchemaRouterOptions;
import io.vertx.json.schema.common.dsl.ObjectSchemaBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.rxjava3.core.AbstractVerticle;
import io.vertx.rxjava3.ext.web.Router;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import io.vertx.rxjava3.ext.web.validation.ValidationHandler;
import io.vertx.rxjava3.json.schema.SchemaParser;
import io.vertx.rxjava3.json.schema.SchemaRouter;
import io.vertx.rxjava3.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.vertx.json.schema.common.dsl.Keywords.maxLength;
import static io.vertx.json.schema.common.dsl.Keywords.minLength;
import static io.vertx.json.schema.common.dsl.Schemas.objectSchema;
import static io.vertx.json.schema.common.dsl.Schemas.stringSchema;

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
    public Completable rxStart() {
        log.info("Starting HTTP server...");
        InternalLoggerFactory.setDefaultFactory(Slf4JLoggerFactory.INSTANCE);

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
            .rxListen(8888)
            // Print the port
            .ignoreElement()
            ;
    }

    //create routes
    private Router routes(PostsHandler handlers) {

        // Create a Router
        Router router = Router.router(vertx);
        // register BodyHandler globally.
        //router.route().handler(BodyHandler.create());
        router.get("/posts").produces("application/json").handler(handlers::all);

        SchemaRouter schemaRouter = SchemaRouter.create(vertx, new SchemaRouterOptions());
        SchemaParser schemaParser = SchemaParser.createDraft201909SchemaParser(schemaRouter);

        ObjectSchemaBuilder bodySchemaBuilder = objectSchema()
            .requiredProperty("title", stringSchema().with(minLength(5)).with(maxLength(100)))
            .requiredProperty("content", stringSchema().with(minLength(10)).with(maxLength(2000)));

        ValidationHandler validation = ValidationHandler.newInstance(
            ValidationHandler
                .builder(schemaParser)
                .body(Bodies.json(bodySchemaBuilder))
                //.body(Bodies.formUrlEncoded(bodySchemaBuilder))
                .build()
        );

        Handler<RoutingContext> validationFailureHandler = (RoutingContext rc) -> {
            if (rc.failure() instanceof BadRequestException) {
                rc.response()
                    .setStatusCode(400)
                    .end("validation failed.");
            }
        };

        router.post("/posts").consumes("application/json")
            .handler(validation)
            .handler(BodyHandler.create())
            .handler(handlers::save)
            .failureHandler(validationFailureHandler);

        router.get("/posts/:id").produces("application/json")
            .handler(handlers::get)
            .failureHandler(frc -> frc.response().setStatusCode(404).end());

        router.put("/posts/:id")
            .consumes("application/json")
            .handler(BodyHandler.create()).handler(handlers::update);

        router.delete("/posts/:id")
            .handler(handlers::delete);

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
