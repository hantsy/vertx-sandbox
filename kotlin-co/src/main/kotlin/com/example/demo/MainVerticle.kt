package com.example.demo

import com.example.verticle.CoroutineBaseVerticle
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.vertx.core.json.jackson.DatabindCodec
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.kotlin.core.http.httpServerOptionsOf
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.await
import io.vertx.pgclient.PgConnectOptions
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.PoolOptions
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.Logger

class MainVerticle : CoroutineBaseVerticle() {
    companion object {
        private val LOGGER = Logger.getLogger(MainVerticle::class.java.name)

        /**
         * Configure logging from logging.properties file.
         * When using custom JUL logging properties, named it to vertx-default-jul-logging.properties
         * or set java.util.logging.config.file system property to locate the properties file.
         */
        @Throws(IOException::class)
        private fun setupLogging() {
            MainVerticle::class.java.getResourceAsStream("/logging.properties")
                .use { f -> LogManager.getLogManager().readConfiguration(f) }
        }

        init {
            LOGGER.info("Customizing the built-in jackson ObjectMapper...")
            val objectMapper = DatabindCodec.mapper()
            objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            objectMapper.disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
            objectMapper.disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
            val module = JavaTimeModule()
            objectMapper.registerModule(module)
        }
    }

    override suspend fun start() {
        LOGGER.log(Level.INFO, "Starting HTTP server...")
        //setupLogging();

        //Create a PgPool instance
        val pgPool = pgPool()

        //Creating PostRepository
        val postRepository = PostRepository(pgPool)

        //Creating PostHandler
        val postHandlers = PostsHandler(postRepository)

        // Initializing the sample data
        val initializer = DataInitializer(pgPool)
        initializer.run()

        // Configure routes
        val router = routes(postHandlers)

        // Create the HTTP server
        val options = httpServerOptionsOf(idleTimeout = 5, idleTimeoutUnit = TimeUnit.MINUTES, logActivity = true)
        vertx.createHttpServer(options) // Handle every request using the router
            .requestHandler(router) // Start listening
            .listen(8888) // Print the port
            .onComplete { println("HttpSever started at ${it.result().actualPort()}") }
            .await()
    }

    override suspend fun stop() {
        super.stop()
    }

    //create routes
    private fun routes(handlers: PostsHandler): Router {

        // Create a Router
        val router = Router.router(vertx)
        // register BodyHandler globally.
        //router.route().handler(BodyHandler.create());

        router.get("/posts")
            .produces("application/json")
            .coroutineHandler {
                handlers.all(it)
            }

        router.post("/posts")
            .consumes("application/json")
            .handler(BodyHandler.create())
            .coroutineHandler {
                handlers.save(it)
            }

        router.get("/posts/:id")
            .produces("application/json")
            .coroutineHandler {
                handlers.getById(it)
            }


        router.put("/posts/:id")
            .consumes("application/json")
            .handler(BodyHandler.create())
            .coroutineHandler {
                handlers.update(it)
            }

        router.delete("/posts/:id")
            .coroutineHandler {
                handlers.delete(it)
            }

        router.route().failureHandler {
            if (it.failure() is PostNotFoundException) {
                it.response()
                    .setStatusCode(404)
                    .end(
                        json {
                            obj(
                                "message" to "${it.failure().message}",
                                "code" to "not_found"
                            )
                        }.toString()
                    )
            }
        }

        router.get("/hello").handler { it.response().end("Hello from my route") }

        return router
    }

    private fun pgPool(): PgPool {
        val connectOptions = PgConnectOptions()
            .setPort(5432)
            .setHost("localhost")
            .setDatabase("blogdb")
            .setUser("user")
            .setPassword("password")

        // Pool Options
        val poolOptions = PoolOptions().setMaxSize(5)

        // Create the pool from the data object
        return PgPool.pool(vertx, connectOptions, poolOptions)
    }
}
