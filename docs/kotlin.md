# Building RESTful APIs with Eclipse Vertx  and Kotlin

As mentioned in the former posts, Eclipse Vertx expands its API to different languages such as Kotlin, Groovy via official bindings, and even Node/Typescript and PHP via community supports.

In this post, we will reimplement the former RESTful APIs with Kotlin language.

Open your browser and navigate to [Eclipse Vertx Starter](https://start.vertx.io), and generate the project skeleton. Do not forget to select **Kotlin** in the *language* field.

For the existing project, add the following dependency into the *pom.xml* file.

```xml
<dependency>
    <groupId>io.vertx</groupId>
    <artifactId>vertx-lang-kotlin</artifactId>
</dependency>
```

Configure `kotlin-compiler-plugin` to compile the Kotlin source codes.

```xml
<plugin>
    <groupId>org.jetbrains.kotlin</groupId>
    <artifactId>kotlin-maven-plugin</artifactId>
    <version>${kotlin.version}</version>
    <configuration>
        <jvmTarget>16</jvmTarget>
    </configuration>
    <executions>
        <execution>
            <id>compile</id>
            <goals>
                <goal>compile</goal>
            </goals>
        </execution>
        <execution>
            <id>test-compile</id>
            <goals>
                <goal>test-compile</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

We will use the same file structure and migrate [the original project(written in Java)](https://github.com/hantsy/vertx-sandbox/tree/master/post-service) to Kotlin.

Firstly let's have a look at the entry class -  `MainVerticle`.

```kotlin
class MainVerticle : AbstractVerticle() {
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

    @Throws(Exception::class)
    override fun start(startPromise: Promise<Void>) {
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
        vertx.createHttpServer() // Handle every request using the router
            .requestHandler(router) // Start listening
            .listen(8888) // Print the port
            .onSuccess {
                startPromise.complete()
                println("HTTP server started on port " + it.actualPort())
            }
            .onFailure {
                startPromise.fail(it)
                println("Failed to start HTTP server:" + it.message)
            }
    }

    //create routes
    private fun routes(handlers: PostsHandler): Router {

        // Create a Router
        val router = Router.router(vertx)
        // register BodyHandler globally.
        //router.route().handler(BodyHandler.create());
        router.get("/posts")
            .produces("application/json")
            .handler { handlers.all(it) }

        router.post("/posts")
            .consumes("application/json")
            .handler(BodyHandler.create())
            .handler { handlers.save(it) }

        router.get("/posts/:id")
            .produces("application/json")
            .handler { handlers.getById(it) }
            .failureHandler {
                val error = it.failure()
                if (error is PostNotFoundException) {
                    it.response().setStatusCode(404).end(error.message)
                }
            }

        router.put("/posts/:id")
            .consumes("application/json")
            .handler(BodyHandler.create())
            .handler { handlers.update(it) }

        router.delete("/posts/:id")
            .handler { handlers.delete(it) }

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
```



In this class we move the original *static* block to a *companion object*.

In the router function,  it assembles request handlers in routes. Let's have a look at the `PostsHandlers` class.

```kotlin
class PostsHandler(val posts: PostRepository) {
    fun all(rc: RoutingContext) {
//        var params = rc.queryParams();
//        var q = params.get("q");
//        var limit = params.get("limit") == null ? 10 : Integer.parseInt(params.get("q"));
//        var offset = params.get("offset") == null ? 0 : Integer.parseInt(params.get("offset"));
//        LOGGER.log(Level.INFO, " find by keyword: q={0}, limit={1}, offset={2}", new Object[]{q, limit, offset});
        posts.findAll()
            .onSuccess {
                rc.response().end(Json.encode(it))
            }

    }

    fun getById(rc: RoutingContext) {
        val params = rc.pathParams()
        val id = params["id"]
        posts.findById(UUID.fromString(id))
            .onSuccess { rc.response().end(Json.encode(it)) }
            .onFailure { rc.fail(404, it) }
    }

    fun save(rc: RoutingContext) {
        //rc.getBodyAsJson().mapTo(PostForm.class)
        val body = rc.bodyAsJson
        LOGGER.log(Level.INFO, "request body: {0}", body)
        val (title, content) = body.mapTo(CreatePostCommand::class.java)
        posts.save(Post(title = title, content = content))
            .onSuccess { savedId: UUID ->
                rc.response()
                    .putHeader("Location", "/posts/$savedId")
                    .setStatusCode(201)
                    .end()
            }
    }

    fun update(rc: RoutingContext) {
        val params = rc.pathParams()
        val id = params["id"]
        val body = rc.bodyAsJson
        LOGGER.log(Level.INFO, "\npath param id: {0}\nrequest body: {1}", arrayOf(id, body))
        var (title, content) = body.mapTo(CreatePostCommand::class.java)
        posts.findById(UUID.fromString(id))
            .flatMap { post: Post ->
                post.apply {
                    title = title
                    content = content
                }
                posts.update(post)
            }
            .onSuccess { rc.response().setStatusCode(204).end() }
            .onFailure { rc.fail(it) }
    }

    fun delete(rc: RoutingContext) {
        val params = rc.pathParams()
        val id = params["id"]
        val uuid = UUID.fromString(id)
        posts.findById(uuid)
            .flatMap { posts.deleteById(uuid) }
            .onSuccess { rc.response().setStatusCode(204).end() }
            .onFailure { rc.fail(404, it) }
    }

    companion object {
        private val LOGGER = Logger.getLogger(PostsHandler::class.java.simpleName)
    }
}
```

Let's move to the `PostRepository` class.

```kotlin
class PostRepository(private val client: PgPool) {

    fun findAll() = client.query("SELECT * FROM posts ORDER BY id ASC")
        .execute()
        .map { rs: RowSet<Row?> ->
            StreamSupport.stream(rs.spliterator(), false)
                .map { mapFun(it!!) }
                .toList()
        }


    fun findById(id: UUID) = client.preparedQuery("SELECT * FROM posts WHERE id=$1")
        .execute(Tuple.of(id))
        .map { it.iterator() }
        .map {
            if (it.hasNext()) mapFun(it.next());
            throw PostNotFoundException(id)
        }


    fun save(data: Post) = client.preparedQuery("INSERT INTO posts(title, content) VALUES ($1, $2) RETURNING (id)")
        .execute(Tuple.of(data.title, data.content))
        .map { it.iterator().next().getUUID("id") }


    fun saveAll(data: List<Post>): Future<Int> {
        val tuples = data.map { Tuple.of(it.title, it.content) }

        return client.preparedQuery("INSERT INTO posts (title, content) VALUES ($1, $2)")
            .executeBatch(tuples)
            .map { it.rowCount() }
    }

    fun update(data: Post) = client.preparedQuery("UPDATE posts SET title=$1, content=$2 WHERE id=$3")
        .execute(Tuple.of(data.title, data.content, data.id))
        .map { it.rowCount() }


    fun deleteAll() = client.query("DELETE FROM posts").execute()
        .map { it.rowCount() }


    fun deleteById(id: UUID) = client.preparedQuery("DELETE FROM posts WHERE id=$1").execute(Tuple.of(id))
        .map { it.rowCount() }

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

The  POJO classes are converted to Kotlin data classes.

```kotlin
//Models.kt
data class Post(
    var id: UUID? = null,
    var title: String,
    var content: String,
    var createdAt: LocalDateTime? = LocalDateTime.now()
)

data class CreatePostCommand(
    val title: String,
    val content: String
)
```

The `DataIntializer` is still used to insert some sample data at the application startup.

```kotlin
class DataInitializer(private val client: PgPool) {

    fun run() {
        LOGGER.info("Data initialization is starting...")
        val first = Tuple.of("Hello Quarkus", "My first post of Quarkus")
        val second = Tuple.of("Hello Again, Quarkus", "My second post of Quarkus")
        client
            .withTransaction { conn: SqlConnection ->
                conn.query("DELETE FROM posts").execute()
                    .flatMap {
                        conn.preparedQuery("INSERT INTO posts (title, content) VALUES ($1, $2)")
                            .executeBatch(listOf(first, second))
                    }
                    .flatMap {
                        conn.query("SELECT * FROM posts").execute()
                    }
            }
            .onSuccess { data: RowSet<Row?> ->
                StreamSupport.stream(data.spliterator(), true)
                    .forEach {
                        LOGGER.log(Level.INFO, "saved data:{0}", it!!.toJson())
                    }
            }
            .onComplete {
                //client.close(); will block the application.
                LOGGER.info("Data initialization is done...")
            }
            .onFailure { LOGGER.warning("Data initialization is failed:" + it.message) }
    }

    companion object {
        private val LOGGER = Logger.getLogger(DataInitializer::class.java.name)
    }
}
```

Run the application via maven command.

```bash
mvn clean compile exec:java
```

Additionally, Vertx Kotlin bindings provides a `Json` DSL extension to simplify the JSON encoding.

```kotli
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
```

Get [the source codes](https://github.com/hantsy/vertx-sandbox/tree/master/kotlin) from my Github.

