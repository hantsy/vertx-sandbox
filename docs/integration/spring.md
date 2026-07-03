# Integrating Vert.x Application with Spring Framework

As shown in the former post, we assembled everything manually in the `MainVerticle` class.

```java
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
vertx.createHttpServer()...
```

In this post, we will introduce the Spring framework to manage the dependencies of the above items.

## Project Setup

This module is built with:

- **Vert.x 5.1.3** (via `vertx-stack-depchain` BOM)
- **Java 25** (`maven.compiler.release=25`)
- **Spring Framework 7.0.3** (via `spring-framework-bom` BOM)
- **Jackson 2.22.0** (via `jackson-bom`)
- **JUnit 6.0.2**, AssertJ 3.27.7
- **Maven Shade Plugin 3.6.1**, Exec Maven Plugin 3.6.3

The project uses `com.example.demo.DemoApplication` as the main class (not `VertxApplication`).

Add the Spring context dependency into the project *pom.xml* file.

```xml
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-context</artifactId>
</dependency>
```

The `spring-context` provides basic IOC functionality which is used for assembling dependencies and providing dependency injection.

## DemoApplication

Create a `@Configuration` class to start the Spring application context.

```java
@Configuration
@ComponentScan
public class DemoApplication {

    public static void main(String[] args) {
        var context = new AnnotationConfigApplicationContext(DemoApplication.class);
        var vertx = context.getBean(Vertx.class);
        var factory = context.getBean(VerticleFactory.class);

        // deploy MainVerticle via verticle identifier name
        vertx.deployVerticle(factory.prefix() + ":" + MainVerticle.class.getName());
    }

    @Bean
    public Vertx vertx(VerticleFactory verticleFactory) {
        Vertx vertx = Vertx.vertx();
        vertx.registerVerticleFactory(verticleFactory);
        return vertx;
    }

    @Bean
    public Pool pgPool(Vertx vertx) {
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
```

Key changes from Vert.x 4:
- Uses `PgBuilder.pool()` builder pattern instead of `PgPool.pool(vertx, connectOptions, poolOptions)`
- The `Pool` interface (from `io.vertx.sqlclient.Pool`) is returned instead of `PgPool` directly

In the main method, we use an `AnnotationConfigApplicationContext` to scan Spring components and assemble the dependencies. Then we fetch the `Vertx` bean and `VerticleFactory` bean from the Spring application context, and call `vertx.deployVerticle`.

> The `VerticleFactory` is a Vert.x built-in hook for instantiating a Verticle instance.

## SpringAwareVerticleFactory

Let's have a look at the `VerticleFactory` bean — a Spring context-aware VerticleFactory implementation.

```java
@Component
public class SpringAwareVerticleFactory implements VerticleFactory, ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Override
    public String prefix() {
        return "spring";
    }

    @Override
    public void createVerticle2(String verticleName, ClassLoader classLoader, Promise<Callable<? extends Deployable>> promise) {
        String clazz = VerticleFactory.removePrefix(verticleName);
        promise.complete(() -> (VerticleBase) applicationContext.getBean(Class.forName(clazz)));
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
```

Note: In Vert.x 5, the `createVerticle2` method uses `Promise<Callable<? extends Deployable>>` and returns `VerticleBase` instead of `Verticle`.

## MainVerticle

The `MainVerticle` class extends `VerticleBase` (replacing `AbstractVerticle` from Vert.x 4) and uses `Future<?> start()` instead of the old `start(Promise<Void>)`.

```java
@Component
public class MainVerticle extends VerticleBase {
    private static final Logger LOGGER = Logger.getLogger(MainVerticle.class.getName());

    static {
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
    public Future<?> start() throws Exception {
        LOGGER.log(Level.INFO, "Starting HTTP server...");
        var router = routes(postHandlers);
        return vertx.createHttpServer()
            .requestHandler(router)
            .listen(8888)
            .onSuccess(server ->
                LOGGER.log(Level.INFO, "HTTP server started on port " + server.actualPort()))
            .onFailure(event ->
                LOGGER.log(Level.SEVERE, "Failed to start HTTP server:" + event.getMessage()));
    }

    private Router routes(PostsHandler handlers) {
        Router router = Router.router(vertx);
        router.get("/posts").produces("application/json").handler(handlers::all);
        router.post("/posts").consumes("application/json").handler(BodyHandler.create()).handler(handlers::save);
        router.get("/posts/:id").produces("application/json").handler(handlers::get).failureHandler(frc -> frc.response().setStatusCode(404).end());
        router.put("/posts/:id").consumes("application/json").handler(BodyHandler.create()).handler(handlers::update);
        router.delete("/posts/:id").handler(handlers::delete);
        router.get("/hello").handler(rc -> rc.response().end("Hello from my route"));
        return router;
    }
}
```

## Other Components

The other classes are declared as Spring `@Component` directly.

```java
@Component
class PostsHandler {
    private final PostRepository posts;

    PostsHandler(PostRepository posts) {
        this.posts = posts;
    }

    public void all(RoutingContext rc) {
        this.posts.findAll()
            .onSuccess(data -> rc.response().end(Json.encode(data)));
    }

    public void get(RoutingContext rc) {
        var id = rc.pathParams().get("id");
        this.posts.findById(UUID.fromString(id))
            .onSuccess(post -> rc.response().end(Json.encode(post)))
            .onFailure(throwable -> rc.fail(404, throwable));
    }

    public void save(RoutingContext rc) {
        var body = rc.body().asJsonObject();
        var form = body.mapTo(CreatePostCommand.class);
        this.posts.save(new Post(null, form.title(), form.content(), null))
            .onSuccess(savedId -> rc.response()
                .putHeader("Location", "/posts/" + savedId)
                .setStatusCode(201)
                .end());
    }

    public void update(RoutingContext rc) {
        var id = rc.pathParams().get("id");
        var body = rc.body().asJsonObject();
        var form = body.mapTo(CreatePostCommand.class);
        this.posts.findById(UUID.fromString(id))
            .compose(post -> {
                var toUpdated = new Post(post.id(), form.title(), form.content(), null);
                return this.posts.update(toUpdated);
            })
            .onSuccess(data -> rc.response().setStatusCode(204).end())
            .onFailure(throwable -> rc.fail(404, throwable));
    }

    public void delete(RoutingContext rc) {
        var uuid = UUID.fromString(rc.pathParams().get("id"));
        this.posts.findById(uuid)
            .compose(post -> this.posts.deleteById(uuid))
            .onSuccess(data -> rc.response().setStatusCode(204).end())
            .onFailure(throwable -> rc.fail(404, throwable));
    }
}
```

```java
@Component
public class PostRepository {
    private final Pool client;

    public PostRepository(Pool client) {
        this.client = client;
    }

    public Future<List<Post>> findAll() {
        return client.query("SELECT * FROM posts ORDER BY id ASC")
            .execute()
            .map(rs -> StreamSupport.stream(rs.spliterator(), false)
                .map(MAPPER)
                .toList());
    }

    public Future<Post> findById(UUID id) {
        return client.preparedQuery("SELECT * FROM posts WHERE id=$1")
            .execute(Tuple.of(id))
            .map(RowSet::iterator)
            .map(iterator -> {
                if (iterator.hasNext()) return MAPPER.apply(iterator.next());
                throw new PostNotFoundException(id);
            });
    }

    public Future<UUID> save(Post data) {
        return client.preparedQuery("INSERT INTO posts(title, content) VALUES ($1, $2) RETURNING (id)")
            .execute(Tuple.of(data.title(), data.content()))
            .map(rs -> rs.iterator().next().getUUID("id"));
    }
    // ... see source for update, deleteAll, deleteById
}
```

```java
@Component
public class DataInitializer {
    private final Pool client;

    public DataInitializer(Pool client) {
        this.client = client;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void run() throws Exception {
        // uses client.withTransaction() to seed sample data
    }
}
```

```java
public record Post(UUID id, String title, String content, LocalDateTime createdAt) {}

public record CreatePostCommand(String title, String content) {}
```

> Note: The `Post` class is a Java record — immutable and concise.

## Running the Application

This module uses `DemoApplication` as its main class, not the built-in `VertxApplication` launcher.

```xml
<plugin>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.6.1</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>shade</goal>
            </goals>
            <configuration>
                <transformers>
                    <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                        <manifestEntries>
                            <Main-Class>com.example.demo.DemoApplication</Main-Class>
                        </manifestEntries>
                    </transformer>
                    <transformer implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
                </transformers>
                <outputFile>${project.build.directory}/${project.artifactId}-${project.version}-fat.jar</outputFile>
            </configuration>
        </execution>
    </executions>
</plugin>
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <version>3.6.3</version>
    <configuration>
        <mainClass>com.example.demo.DemoApplication</mainClass>
    </configuration>
</plugin>
```

Now run the following command to start the application.

```bash
mvn clean compile exec:java

//or
mvn clean package
java -jar target\xxx-fat.jar
```

## Testing

In the `TestMainVerticle`, we use `SpringJUnitConfig` to bootstrap the Spring application context and `VertxExtension` to manage the Vert.x test lifecycle.

```java
@SpringJUnitConfig(classes = DemoApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(VertxExtension.class)
public class TestMainVerticle {

    @Autowired
    ApplicationContext context;

    Vertx vertx;

    @BeforeAll
    public void setupAll(VertxTestContext testContext) {
        vertx = context.getBean(Vertx.class);
        var factory = context.getBean(VerticleFactory.class);
        vertx.deployVerticle(factory.prefix() + ":" + MainVerticle.class.getName())
            .onSuccess(id -> {
                LOGGER.info("deployed:" + id);
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }

    @Test
    void testGetAll(VertxTestContext testContext) {
        var client = vertx.createHttpClient(new HttpClientOptions().setDefaultPort(8888));
        client.request(HttpMethod.GET, "/posts")
            .flatMap(req -> req.send().flatMap(HttpClientResponse::body))
            .onSuccess(buffer -> testContext.verify(() -> {
                assertThat(buffer.toJsonArray().size()).isGreaterThan(0);
                testContext.completeNow();
            }))
            .onFailure(e -> {
                LOGGER.log(Level.ALL, "error: {0}", e.getMessage());
                testContext.failNow(e);
            });
    }
}
```

Get the [example codes from my GitHub](https://github.com/hantsy/vertx-sandbox/tree/master/spring).

