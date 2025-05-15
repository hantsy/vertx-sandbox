package com.example.demo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.vertx.core.json.jackson.DatabindCodec
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.kotlin.core.http.httpServerOptionsOf
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.CoroutineRouterSupport
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.pgclient.PgBuilder
import io.vertx.pgclient.PgConnectOptions
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.PoolOptions
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.Logger

class MainVerticle : CoroutineVerticle(), CoroutineRouterSupport {
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
            objectMapper.registerKotlinModule()
            objectMapper.registerModule(JavaTimeModule())
            objectMapper.registerModule(Jdk8Module())
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
        val result = vertx.createHttpServer(options) // Handle every request using the router
            .requestHandler(router) // Start listening
            .listen(8888) // Print the port
            // .onComplete { println("HttpSever started at ${it.result().actualPort()}") }
            .coAwait()
        LOGGER.log(Level.FINEST, "HttpServer started at ${result.actualPort()}")
    }

    override suspend fun stop() {
        super.stop()
    }

    //create routes
    private suspend fun routes(handlers: PostsHandler): Router {

        // Create a Router
        val router = Router.router(vertx)
        // register BodyHandler globally.
        //router.route().handler(BodyHandler.create());

        router.get("/posts")
            .produces("application/json")
            .coHandler {
                handlers.all(it)
            }

        router.post("/posts")
            .consumes("application/json")
            .handler(BodyHandler.create())
            .coHandler {
                handlers.save(it)
            }

        router.get("/posts/:id")
            .produces("application/json")
            .coHandler {
                handlers.getById(it)
            }


        router.put("/posts/:id")
            .consumes("application/json")
            .handler(BodyHandler.create())
            .coHandler {
                handlers.update(it)
            }

        router.delete("/posts/:id")
            .coHandler {
                handlers.delete(it)
            }

        router.route().coFailureHandler {
            if (it.failure() is PostNotFoundException) {
                it.response()
                    .setStatusCode(404)
                    .end(
                        json {// an example using JSON DSL
                            obj(
                                "message" to "${it.failure().message}",
                                "code" to "not_found"
                            )
                        }.toString()
                    )
            }
        }

        router.get("/hello").coRespond { it.end("Hello from my route") }

        return router
    }

    private fun pgPool(): Pool {
        val connectOptions = PgConnectOptions()
            .setPort(5432)
            .setHost("localhost")
            .setDatabase("blogdb")
            .setUser("user")
            .setPassword("password")

        // Pool Options
        val poolOptions = PoolOptions().setMaxSize(5)

        // Create the pool from the data object
        return PgBuilder.pool()
            //.withConnectHandler(connectHandler)
            .with(poolOptions)
            .connectingTo(connectOptions)
            .using(vertx)
            .build()
    }

}
