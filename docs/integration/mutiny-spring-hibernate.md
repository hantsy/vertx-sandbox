# Building Vert.x Application with SmallRye Mutiny, Spring and Hibernate

In the [Spring integration post](./spring.md), we used Spring to assemble the resources and start the application.

In this post, we will reuse the Spring base code, but:

- Use **Hibernate Reactive** to replace the raw Postgres Client
- Use **SmallRye Mutiny Vert.x bindings** to replace `Future` with `Uni`

## Project Setup

This module is built with:

- **Vert.x 5.1.3** (via `vertx-stack-depchain` BOM)
- **Java 25** (`maven.compiler.release=25`)
- **SmallRye Mutiny Vert.x 3.21.4** (`smallrye-mutiny-vertx-core`, `-web`, `-pg-client`)
- **Hibernate Reactive 4.2.2.Final** (`hibernate-reactive-core`)
- **Hibernate ORM 7.2.3.Final** (`hibernate-core`, `hibernate-processor`)
- **Spring Boot 4.0.2** (via `spring-boot-dependencies` BOM)
- **JUnit 6.0.2**, AssertJ 3.27.7

The project uses `com.example.demo.DemoApplication` as the main class (not `VertxApplication`).

No `jackson-bom` is required — Jackson version is managed by the `vertx-stack-depchain`.

## Hibernate Dependencies

Add the Hibernate dependencies.

```xml
<dependency>
    <groupId>org.hibernate.reactive</groupId>
    <artifactId>hibernate-reactive-core</artifactId>
    <version>${hibernate-reactive.version}</version>
</dependency>
<dependency>
    <groupId>org.hibernate.orm</groupId>
    <artifactId>hibernate-core</artifactId>
    <version>${hibernate.version}</version>
</dependency>
<dependency>
    <groupId>org.hibernate.orm</groupId>
    <artifactId>hibernate-processor</artifactId>
    <version>${hibernate.version}</version>
    <optional>true</optional>
</dependency>
```

- `hibernate-reactive-core` provides Hibernate Reactive using the Vert.x Postgres Client, returning SmallRye Mutiny `Uni` types.
- `hibernate-processor` generates JPA `Entity` metadata classes (e.g. `Post_`) at compile time via the annotation processor path in the Maven compiler plugin.

## SmallRye Mutiny Dependencies

```xml
<dependency>
    <groupId>io.smallrye.reactive</groupId>
    <artifactId>smallrye-mutiny-vertx-core</artifactId>
    <version>${mutiny-vertx.version}</version>
</dependency>
<dependency>
    <groupId>io.smallrye.reactive</groupId>
    <artifactId>smallrye-mutiny-vertx-web</artifactId>
    <version>${mutiny-vertx.version}</version>
</dependency>
<dependency>
    <groupId>io.smallrye.reactive</groupId>
    <artifactId>smallrye-mutiny-vertx-pg-client</artifactId>
    <version>${mutiny-vertx.version}</version>
</dependency>
```

## persistence.xml

Add a `persistence.xml` configuration in `main/resources/META-INF`.

```xml
<persistence version="3.0" xmlns="https://jakarta.ee/xml/ns/persistence"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="https://jakarta.ee/xml/ns/persistence https://jakarta.ee/xml/ns/persistence/persistence_3_0.xsd">

    <persistence-unit name="blogPU">
        <provider>org.hibernate.reactive.provider.ReactivePersistenceProvider</provider>
        <class>com.example.demo.Post</class>

        <properties>
            <property name="jakarta.persistence.jdbc.url"
                      value="jdbc:postgresql://localhost:5432/blogdb"/>
            <property name="jakarta.persistence.jdbc.user" value="user"/>
            <property name="jakarta.persistence.jdbc.password" value="password"/>
            <property name="hibernate.connection.pool_size" value="10"/>
            <property name="jakarta.persistence.schema-generation.database.action"
                      value="drop-and-create"/>
            <property name="hibernate.show_sql" value="true"/>
            <property name="hibernate.format_sql" value="true"/>
            <property name="hibernate.highlight_sql" value="true"/>
        </properties>
    </persistence-unit>
</persistence>
```

Uses Jakarta Persistence namespace (`jakarta.persistence.*`), and the `ReactivePersistenceProvider` for reactive streams support.

## DemoApplication

```java
@Configuration
@ComponentScan
public class DemoApplication {

    public static void main(String[] args) {
        var context = new AnnotationConfigApplicationContext(DemoApplication.class);
        var vertx = context.getBean(Vertx.class);
        var factory = context.getBean(VerticleFactory.class);

        vertx.deployVerticleAndAwait(factory.prefix() + ":" + MainVerticle.class.getName());
    }

    @Bean
    public Vertx vertx(VerticleFactory verticleFactory) {
        Vertx vertx = Vertx.vertx();
        vertx.registerVerticleFactory(verticleFactory);
        return vertx;
    }

    @Bean
    public Mutiny.SessionFactory sessionFactory() {
        return Persistence.createEntityManagerFactory("blogPU")
            .unwrap(Mutiny.SessionFactory.class);
    }
}
```

> **Important**: All imports use `io.vertx.mutiny.*` (SmallRye Mutiny bindings), including `io.vertx.mutiny.core.Vertx`. The `deployVerticleAndAwait` method blocks the main thread while deploying the verticle.

The `Mutiny.SessionFactory` bean is created by bootstrapping JPA's `EntityManagerFactory` and unwrapping the Mutiny variant.

## Post Entity

The `Post` class is a JPA entity with auto-generated UUID and `@CreationTimestamp`.

```java
@Entity
@Table(name = "posts")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;
    String title;
    String content;

    @Column(name = "created_at")
    @CreationTimestamp
    LocalDateTime createdAt = LocalDateTime.now();

    public Post() {}

    public static Post of(String title, String content) {
        Post post = new Post();
        post.title = title;
        post.content = content;
        return post;
    }
    // getters/setters...
}
```

## PostRepository

```java
@Component
public class PostRepository {
    private static final Logger LOGGER = Logger.getLogger(PostRepository.class.getName());

    private final Mutiny.SessionFactory sessionFactory;

    public PostRepository(Mutiny.SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public Uni<List<Post>> findAll() {
        CriteriaBuilder cb = this.sessionFactory.getCriteriaBuilder();
        CriteriaQuery<Post> query = cb.createQuery(Post.class);
        Root<Post> root = query.from(Post.class);
        return this.sessionFactory.withSession(session -> session.createQuery(query).getResultList());
    }

    public Uni<Post> findById(UUID id) {
        Objects.requireNonNull(id, "id can not be null");
        return this.sessionFactory.withSession(session -> session.find(Post.class, id))
            .onItem().ifNull().failWith(() -> new PostNotFoundException(id));
    }

    public Uni<Post> save(Post post) {
        if (post.getId() == null) {
            return this.sessionFactory.withSession(session ->
                session.persist(post)
                    .chain(session::flush)
                    .replaceWith(post)
            );
        } else {
            return this.sessionFactory.withSession(session ->
                session.merge(post).onItem().call(session::flush));
        }
    }

    public Uni<Integer> deleteById(UUID id) {
        CriteriaBuilder cb = this.sessionFactory.getCriteriaBuilder();
        CriteriaDelete<Post> delete = cb.createCriteriaDelete(Post.class);
        Root<Post> root = delete.from(Post.class);
        delete.where(cb.equal(root.get(Post_.id), id));
        return this.sessionFactory.withTransaction((session, tx) ->
            session.createQuery(delete).executeUpdate()
        );
    }

    // ... see source for findByKeyword, saveAll, deleteAll
}
```

Uses JPA Criteria API with `Mutiny.SessionFactory`, returning `Uni<T>` instead of `Future<T>`.

## PostsHandler

```java
@Component
class PostsHandler {
    private static final Logger LOGGER = Logger.getLogger(PostsHandler.class.getSimpleName());

    private final PostRepository posts;

    PostsHandler(PostRepository posts) {
        this.posts = posts;
    }

    public void all(RoutingContext rc) {
        this.posts.findAll()
            .subscribe()
            .with(data -> {
                    LOGGER.log(Level.INFO, "posts data: {0}", data);
                    rc.response().endAndAwait(Json.encode(data));
                },
                rc::fail);
    }

    public void get(RoutingContext rc) {
        var id = rc.pathParams().get("id");
        this.posts.findById(UUID.fromString(id))
            .subscribe()
            .with(
                post -> rc.response().endAndAwait(Json.encode(post)),
                throwable -> rc.fail(404, throwable));
    }

    public void save(RoutingContext rc) {
        var body = rc.body().asJsonObject();
        var form = body.mapTo(CreatePostCommand.class);

        this.posts.save(Post.of(form.title(), form.content()))
            .subscribe()
            .with(
                savedId -> rc.response()
                    .putHeader("Location", "/posts/" + savedId)
                    .setStatusCode(201)
                    .endAndAwait(),
                throwable -> rc.fail(404, throwable));
    }

    public void update(RoutingContext rc) {
        var id = rc.pathParams().get("id");
        var body = rc.body().asJsonObject();
        var form = body.mapTo(CreatePostCommand.class);

        this.posts.findById(UUID.fromString(id))
            .flatMap(post -> {
                post.setTitle(form.title());
                post.setContent(form.content());
                return this.posts.save(post);
            })
            .subscribe()
            .with(
                data -> rc.response().setStatusCode(204).endAndAwait(),
                throwable -> rc.fail(404, throwable));
    }

    public void delete(RoutingContext rc) {
        var uuid = UUID.fromString(rc.pathParams().get("id"));
        this.posts.findById(uuid)
            .flatMap(post -> this.posts.deleteById(uuid))
            .subscribe()
            .with(data -> rc.response().setStatusCode(204).endAndAwait(),
                throwable -> rc.fail(404, throwable));
    }
}
```

Uses `subscribe().with()` to consume the Uni results, with `endAndAwait` (Mutiny variant) to end the response.

## MainVerticle

The `MainVerticle` extends `io.smallrye.mutiny.vertx.core.AbstractVerticle` (Mutiny base class) and overrides `asyncStart()` returning `Uni<Void>`.

```java
@Component
public class MainVerticle extends AbstractVerticle {
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
    public Uni<Void> asyncStart() {
        LOGGER.log(Level.INFO, "Starting HTTP server...");
        var router = routes(postHandlers);

        return vertx.createHttpServer()
            .requestHandler(router)
            .listen(8888)
            .onItem().invoke(() -> LOGGER.info("Http server is listening on http://127.0.0.1:8888"))
            .onFailure().invoke(Throwable::printStackTrace)
            .replaceWithVoid();
    }

    private Router routes(PostsHandler handlers) {
        Router router = Router.router(vertx);
        router.get("/posts").produces("application/json").handler(handlers::all);
        router.post("/posts").consumes("application/json")
            .handler(BodyHandler.create())
            .handler(handlers::save);
        router.get("/posts/:id").produces("application/json")
            .handler(handlers::get)
            .failureHandler(frc -> frc.response().setStatusCode(404).endAndAwait());
        router.put("/posts/:id").consumes("application/json")
            .handler(BodyHandler.create())
            .handler(handlers::update);
        router.delete("/posts/:id").handler(handlers::delete);
        router.get("/hello").handler(rc -> rc.response().end("Hello from my route"));
        return router;
    }
}
```

> The Mutiny `Router` also supports `respond()` for returning `Uni<T>` directly. See the commented section in the source code for an alternative approach.

## DataInitializer

Seeds sample data using Hibernate Reactive's `withTransaction`.

```java
@Component
public class DataInitializer {

    private final Mutiny.SessionFactory sessionFactory;

    public DataInitializer(Mutiny.SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void run() throws InterruptedException {
        Post first = Post.of("Hello Quarkus", "My first post of Quarkus");
        Post second = Post.of("Hello Again, Quarkus", "My second post of Quarkus");

        var latch = new CountDownLatch(1);
        sessionFactory
            .withTransaction((conn, tx) ->
                conn.createMutationQuery("DELETE FROM Post").executeUpdate()
                    .flatMap(r -> conn.persistAll(first, second))
                    .chain(conn::flush)
                    .flatMap(r -> conn.createSelectionQuery("SELECT p from Post p", Post.class).getResultList())
            )
            .onTermination().invoke(latch::countDown)
            .subscribe()
            .with(data -> LOGGER.log(Level.INFO, "saved data:{0}", data),
                throwable -> LOGGER.warning("Data initialization failed:" + throwable.getMessage()));

        latch.await(500, TimeUnit.MILLISECONDS);
    }
}
```

## Testing

Add the Mutiny JUnit5 test dependency.

```xml
<dependency>
    <groupId>io.smallrye.reactive</groupId>
    <artifactId>smallrye-mutiny-vertx-junit5</artifactId>
    <version>${mutiny-vertx.version}</version>
    <scope>test</scope>
</dependency>
```

```java
@SpringJUnitConfig(classes = DemoApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(VertxExtension.class)
public class TestMainVerticle {

    @Autowired
    ApplicationContext context;

    io.vertx.mutiny.core.Vertx vertx;

    @BeforeAll
    public void setupAll(VertxTestContext testContext) {
        vertx = context.getBean(io.vertx.mutiny.core.Vertx.class);
        var factory = context.getBean(VerticleFactory.class);
        vertx.deployVerticle(factory.prefix() + ":" + MainVerticle.class.getName())
            .subscribe()
            .with(id -> {
                    LOGGER.info("deployed:" + id);
                    testContext.completeNow();
                },
                testContext::failNow);
    }

    @Test
    void testGetAll(VertxTestContext testContext) {
        var client = vertx.createHttpClient(
            new HttpClientOptions().setDefaultPort(8888));
        client.request(HttpMethod.GET, "/posts")
            .flatMap(HttpClientRequest::send)
            .flatMap(HttpClientResponse::body)
            .subscribe()
            .with(buffer -> testContext.verify(() -> {
                    assertThat(buffer.toJsonArray().size()).isGreaterThan(0);
                    testContext.completeNow();
                }),
                e -> {
                    LOGGER.log(Level.ALL, "error: {0}", e.getMessage());
                    testContext.failNow(e);
                });
    }
}
```

Get the [example codes from my GitHub](https://github.com/hantsy/vertx-sandbox/tree/master/mutiny-spring-hibernate).

> By the way, I have no plan to create a sample application demonstrating the combination of CDI/SmallRye Mutiny/Hibernate. If you are interested in that, please explore [Quarkus](https://quarkus.io).

