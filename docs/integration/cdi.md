# Integrating Vert.x Application with Weld/CDI

In [the last post](./spring.md), we introduced a simple approach to integrate Vert.x applications with Spring framework. In this post, we will try to integrate Vert.x application with [CDI](https://www.cdi-spec.org) to replace Spring.

> CDI is a Dependency Injection specification introduced in Java EE 6, now Jakarta EE maintained by the Eclipse Foundation. Weld is a compatible provider of the Jakarta CDI specification.

## Project Setup

This module is built with:

- **Vert.x 5.1.3** (via `vertx-stack-depchain` BOM)
- **Java 25** (`maven.compiler.release=25`)
- **Weld SE 6.0.4.Final** (`weld-se-shaded`)
- **Jandex 3.5.3** (class indexing)
- **Jackson 2.22.0** (via `jackson-bom`)
- **JUnit 6.0.2**, AssertJ 3.27.7

Add the following dependencies.

```xml
<dependency>
    <groupId>org.jboss.weld.se</groupId>
    <artifactId>weld-se-shaded</artifactId>
    <version>${weld.version}</version>
</dependency>
<dependency>
    <groupId>io.smallrye</groupId>
    <artifactId>jandex</artifactId>
    <version>${jandex.version}</version>
</dependency>
```

- `weld-se-shaded` provides a CDI runtime environment for Java SE.
- `jandex` indexes the application classes and speeds up bean discovery.

Add a `beans.xml` configuration in `main/resources/META-INF` to enable CDI support in a Java SE application.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://xmlns.jcp.org/xml/ns/javaee"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/javaee
                      http://xmlns.jcp.org/xml/ns/javaee/beans_2_0.xsd"
       bean-discovery-mode="annotated">
</beans>
```

> In Jakarta EE, CDI is enabled by default since Java EE 7, and `beans.xml` is optional.

## DemoApplication

Similar to the Spring version, add a `DemoApplication` to start the application.

```java
public class DemoApplication {

    private static final Logger LOGGER = Logger.getLogger(DemoApplication.class.getName());

    public static void main(String[] args) {
        var weld = new Weld();
        WeldContainer container = weld.initialize();
        Vertx vertx = container.select(Vertx.class).get();
        VerticleFactory factory = container.select(VerticleFactory.class).get();

        LOGGER.info("vertx clazz:" + vertx.getClass().getName());
        LOGGER.info("factory clazz:" + factory.getClass().getName());
        vertx.deployVerticle(factory.prefix() + ":" + MainVerticle.class.getName());
    }
}
```

Here we use `weld.initialize()` to initialize the CDI container, then retrieve the `Vertx` bean and `VerticleFactory` bean, and deploy the `MainVerticle`.

## CdiAwareVerticleFactory

Similar to `SpringAwareVerticleFactory`, create a CDI-aware `VerticleFactory`.

```java
@ApplicationScoped
public class CdiAwareVerticleFactory implements VerticleFactory {

    @Inject
    private Instance<Object> instance;

    @Override
    public String prefix() {
        return "cdi";
    }

    @Override
    public void createVerticle2(String verticleName, ClassLoader classLoader, Promise<Callable<? extends Deployable>> promise) {
        String clazz = VerticleFactory.removePrefix(verticleName);
        promise.complete(() -> (VerticleBase) instance.select(Class.forName(clazz)).get());
    }
}
```

Note: In Vert.x 5, the method signature uses `createVerticle2` with `Promise<Callable<? extends Deployable>>` and returns `VerticleBase`.

## Resources

Create a `Resources` class to expose the `Vertx` and `Pool` beans.

```java
@ApplicationScoped
public class Resources {
    private final static Logger LOGGER = Logger.getLogger(Resources.class.getName());

    @Produces
    @Singleton
    public Vertx vertx(VerticleFactory verticleFactory) {
        Vertx vertx = Vertx.vertx();
        vertx.registerVerticleFactory(verticleFactory);
        return vertx;
    }

    @Produces
    public Pool pgPool(Vertx vertx) {
        PgConnectOptions connectOptions = new PgConnectOptions()
            .setPort(5432)
            .setHost("localhost")
            .setDatabase("blogdb")
            .setUser("user")
            .setPassword("password");

        PoolOptions poolOptions = new PoolOptions().setMaxSize(5);

        return PgBuilder.pool()
            .with(poolOptions)
            .connectingTo(connectOptions)
            .using(vertx)
            .build();
    }

    public void disposesPgPool(@Disposes Pool pgPool) {
        LOGGER.info("disposing PgPool...");
        pgPool.close().onSuccess(v -> LOGGER.info("PgPool is closed successfully."));
    }
}
```

Key changes from Vert.x 4:
- Uses `PgBuilder.pool()` builder pattern instead of `PgPool.pool(vertx, connectOptions, poolOptions)`
- Returns `Pool` (from `io.vertx.sqlclient.Pool`) instead of `PgPool`

> We annotate the `Vertx` bean with `@Singleton` (not `@ApplicationScoped`) because Weld does not create proxy objects for `@Singleton` beans. Without this, there would be a class casting error at startup.

## MainVerticle

`MainVerticle` extends `VerticleBase` (replacing `AbstractVerticle` from Vert.x 4) and uses `Future<?> start()`.

```java
@Dependent
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

    private PostsHandler postsHandler;

    public MainVerticle() {}

    @Inject
    public MainVerticle(PostsHandler postHandlers) {
        this.postsHandler = postHandlers;
    }

    @Override
    public Future<?> start() throws Exception {
        LOGGER.log(Level.INFO, "Starting HTTP server...");
        var router = routes(postsHandler);
        return vertx.createHttpServer()
            .requestHandler(router)
            .listen(8888)
            .onSuccess(server -> LOGGER.log(Level.INFO, "HTTP server started on port " + server.actualPort()))
            .onFailure(event -> LOGGER.log(Level.SEVERE, "Failed to start HTTP server:" + event.getMessage()));
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

## Other Beans

Other beans use CDI `@ApplicationScoped` to replace Spring's `@Component`.

```java
@ApplicationScoped
class PostsHandler {
    private PostRepository posts;

    public PostsHandler() {}

    @Inject
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
@ApplicationScoped
public class PostRepository {
    private Pool client;

    public PostRepository() {}

    @Inject
    public PostRepository(Pool client) {
        this.client = client;
    }

    public Future<List<Post>> findAll() {
        return client.query("SELECT * FROM posts ORDER BY id ASC")
            .execute()
            .map(rs -> StreamSupport.stream(rs.spliterator(), false)
                .map(MAPPER)
                .collect(toList()));
    }

    // ... see source for findById, save, update, deleteAll, deleteById
}
```

```java
@ApplicationScoped
public class DataInitializer {
    private Pool client;

    public DataInitializer() {}

    @Inject
    public DataInitializer(Pool client) {
        this.client = client;
    }

    public void run(@Observes @Initialized(ApplicationScoped.class) Object o) throws InterruptedException {
        // seeds sample data using client.withTransaction()
    }
}
```

## Testing

Add the Weld JUnit5 dependency for testing.

```xml
<dependency>
    <groupId>org.jboss.weld</groupId>
    <artifactId>weld-junit5</artifactId>
    <version>5.0.3.Final</version>
    <scope>test</scope>
    <exclusions>
        <exclusion>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-api</artifactId>
        </exclusion>
        <exclusion>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-engine</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

```java
@EnableAutoWeld
@ExplicitParamInjection
@AddPackages(DemoApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(VertxExtension.class)
public class TestMainVerticle {

    @Inject
    Instance<Object> context;

    Vertx vertx;

    @BeforeAll
    public void setupAll(VertxTestContext testContext) {
        vertx = context.select(Vertx.class).get();
        var factory = context.select(VerticleFactory.class).get();
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
            .onFailure(e -> LOGGER.log(Level.ALL, "error: {0}", e.getMessage()));
    }
}
```

Get the [example codes from my GitHub](https://github.com/hantsy/vertx-sandbox/tree/master/cdi).

