# Building RESTful APIs with Eclipse Vertx  and Kotlin Coroutines

In [the last post](./kotlin.md), we rebuild the example application with Kotlin language. Besides basic language support, Eclipse Vertx's Kotlin bindings provides Kotlin extensions to convert Vertx's `Future` to Kotlin Coroutines.

Follow the steps in the last post to create a Kotlin based Vertx project, we will rebuild the project with Kotlin Coroutines. 

Firstly let's have a look at the `PostRepository`.

```kotlin
class PostRepository(private val client: PgPool) {

    suspend fun findAll() = client.query("SELECT * FROM posts ORDER BY id ASC")
        .execute()
        .map { rs: RowSet<Row?> ->
            StreamSupport.stream(rs.spliterator(), false)
                .map { mapFun(it!!) }
                .toList()
        }.await()


    suspend fun findById(id: UUID): Post? = client.preparedQuery("SELECT * FROM posts WHERE id=$1")
        .execute(Tuple.of(id))
        .map { it.iterator() }
        .map { if (it.hasNext()) mapFun(it.next()) else null }
        .await()


    suspend fun save(data: Post) =
        client.preparedQuery("INSERT INTO posts(title, content) VALUES ($1, $2) RETURNING (id)")
            .execute(Tuple.of(data.title, data.content))
            .map { it.iterator().next().getUUID("id") }
            .await()


    suspend fun saveAll(data: List<Post>): Int? {
        val tuples = data.map { Tuple.of(it.title, it.content) }

        return client.preparedQuery("INSERT INTO posts (title, content) VALUES ($1, $2)")
            .executeBatch(tuples)
            .map { it.rowCount() }
            .await()
    }

    suspend fun update(data: Post) = client.preparedQuery("UPDATE posts SET title=$1, content=$2 WHERE id=$3")
        .execute(Tuple.of(data.title, data.content, data.id))
        .map { it.rowCount() }
        .await()


    suspend fun deleteAll() = client.query("DELETE FROM posts").execute()
        .map { it.rowCount() }
        .await()


    suspend fun deleteById(id: UUID) = client.preparedQuery("DELETE FROM posts WHERE id=$1").execute(Tuple.of(id))
        .map { it.rowCount() }
        .await()

    companion object {
        private val LOGGER = Logger.getLogger(PostRepository::class.java.name)
        val mapFun: (Row) -> Post = { row: Row ->
            Post(
                row.getUUID("id"),
                row.getString("title"),
                row.getString("content"),
                row.getLocalDateTime("created_at")
            )
        }

    }
}
```

 As you see it is almost same as the Kotlin version, but at the end  line of every function it calls an `await` method to  return a *suspended* result.

Let's move to `PostHandlers`.

```kotlin
class PostsHandler(val posts: PostRepository) {
    suspend fun all(rc: RoutingContext) {
//        var params = rc.queryParams();
//        var q = params.get("q");
//        var limit = params.get("limit") == null ? 10 : Integer.parseInt(params.get("q"));
//        var offset = params.get("offset") == null ? 0 : Integer.parseInt(params.get("offset"));
//        LOGGER.log(Level.INFO, " find by keyword: q={0}, limit={1}, offset={2}", new Object[]{q, limit, offset});
        val data = posts.findAll()
        rc.response().end(Json.encode(data)).await()
    }

    suspend fun getById(rc: RoutingContext) {
        val params = rc.pathParams()
        val id = params["id"]
        val uuid = UUID.fromString(id)
        val data = posts.findById(uuid)
        if (data != null) {
            rc.response().end(Json.encode(data)).await()
        } else {
            rc.fail(404, PostNotFoundException(uuid))
        }
    }

    suspend fun save(rc: RoutingContext) {
        //rc.getBodyAsJson().mapTo(PostForm.class)
        val body = rc.bodyAsJson
        LOGGER.log(Level.INFO, "request body: {0}", body)
        val (title, content) = body.mapTo(CreatePostCommand::class.java)
        val savedId = posts.save(Post(title = title, content = content))
        rc.response()
            .putHeader("Location", "/posts/$savedId")
            .setStatusCode(201)
            .end()
            .await()

    }

    suspend fun update(rc: RoutingContext) {
        val params = rc.pathParams()
        val id = params["id"]
        val uuid = UUID.fromString(id)
        val body = rc.bodyAsJson
        LOGGER.log(Level.INFO, "\npath param id: {0}\nrequest body: {1}", arrayOf(id, body))
        var (title, content) = body.mapTo(CreatePostCommand::class.java)

        var existing: Post? = posts.findById(uuid)
        if (existing != null) {
            val data: Post = existing.apply {
                title = title
                content = content
            }
            posts.update(data)
            rc.response().setStatusCode(204).end().await()
        } else {
            rc.fail(404, PostNotFoundException(uuid))
        }
    }

    suspend fun delete(rc: RoutingContext) {
        val params = rc.pathParams()
        val id = params["id"]
        val uuid = UUID.fromString(id)
        val existing = posts.findById(uuid)
        if (existing != null) {
            rc.response().setStatusCode(204).end().await()
        } else {
            rc.fail(404, PostNotFoundException(uuid))
        }
    }

    companion object {
        private val LOGGER = Logger.getLogger(PostsHandler::class.java.simpleName)
    }
}
```

In the above codes, it uses sequential statements instead of `Future` with chained functionalities

Eclipse Vertx Kotlin bindings provides a `CooutineVerticle`.

```kotlin
class MainVerticle : CoroutineVerticle() {
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
                        json {// an example using JSON DSL
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

    private fun Route.coroutineHandler(fn: suspend (RoutingContext) -> Unit) = handler {
        launch(it.vertx().dispatcher()) {
            try {
                fn(it)
            } catch (e: Exception) {
                it.fail(e)
            }
        }
    }

}
```

Currently Kotlin bindings does not provides a handler methods to accept a coroutine based `RequestHandler`. We create `coroutineHandler` extension method to overcome this issue temporarily, see issue [vert-x3/vertx-lang-kotlin #194](https://github.com/vert-x3/vertx-lang-kotlin/issues/194).

Let's convert the `DataIntializer` to use Kotlin Coroutines.

```kotlin
class DataInitializer(private val client: PgPool) {

    suspend fun run() {
        LOGGER.info("Data initialization is starting...")
        val first = Tuple.of("Hello Quarkus", "My first post of Quarkus")
        val second = Tuple.of("Hello Again, Quarkus", "My second post of Quarkus")


        val result = client
            .withTransaction { conn: SqlConnection ->
                conn.query("DELETE FROM posts")
                    .execute()
                    .flatMap {
                        conn.preparedQuery("INSERT INTO posts (title, content) VALUES ($1, $2)")
                            .executeBatch(listOf(first, second))
                    }
                    .flatMap {
                        conn.query("SELECT * FROM posts")
                            .execute()
                    }

            }.await()

        result.forEach { println(it.toJson()) }
        LOGGER.info("Data initialization is done...")
    }

    companion object {
        private val LOGGER = Logger.getLogger(DataInitializer::class.java.name)
    }
}
```

Get [the example codes](https://github.com/hantsy/vertx-sandbox/tree/master/kotlin) from my Github.