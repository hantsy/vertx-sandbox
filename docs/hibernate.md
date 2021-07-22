# Building Vertx application with SmallRye Mutiny, Spring and Hibernate 

In the [Spring integration post](./spring.md), we use Spring to assemble the resources and start up the application.

In this post,  we will reuse the Spring base codes, but:

*  Use Hibernate Reactive to replace the raw Postgres Client 
* and use SmallRye Mutiny Vertx bindings to replace the original `Future` etc.

Add the Hibernate related dependencies into the project *pom.xml* file.

```xml
<dependency>
    <groupId>org.hibernate.reactive</groupId>
    <artifactId>hibernate-reactive-core</artifactId>
    <version>${hibernate-reactive.version}</version>
</dependency>
<!-- https://mvnrepository.com/artifact/org.hibernate/hibernate-jpamodelgen -->
<dependency>
    <groupId>org.hibernate</groupId>
    <artifactId>hibernate-jpamodelgen</artifactId>
    <version>${hibernate.version}</version>
    <scope>provided</scope>
</dependency>
```

In the above codes, 

* The `hibernate-reactive-core` provides Hibernate Reactive which use Vertx Postgres Client, etc to implement reactive APIs, currently it supports Java 8 `CompletionStage` and SmallRye Mutiny. In this post, we only uses the SmallRye Mutiny APIs.
* The `hibernate-jpamodelgen` will generate the JPA `Entity` metadata classes when compiling the project.

Add a *persistence.xml* configuration in the *main/resources/META-INF* folder.

```xml
<persistence xmlns="http://xmlns.jcp.org/xml/ns/persistence"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/persistence http://xmlns.jcp.org/xml/ns/persistence/persistence_2_2.xsd"
             version="2.2">

    <persistence-unit name="blogPU">
        <provider>org.hibernate.reactive.provider.ReactivePersistenceProvider</provider>

        <class>com.example.demo.Post</class>

        <properties>

            <!-- PostgreSQL -->
            <property name="javax.persistence.jdbc.url"
                      value="jdbc:postgresql://localhost:5432/blogdb"/>

            <!-- Credentials -->
            <property name="javax.persistence.jdbc.user"
                      value="user"/>
            <property name="javax.persistence.jdbc.password"
                      value="password"/>

            <!-- The Vert.x SQL Client connection pool size -->
            <property name="hibernate.connection.pool_size"
                      value="10"/>

            <!-- Automatic schema export -->
            <property name="javax.persistence.schema-generation.database.action"
                      value="drop-and-create"/>

            <!-- SQL statement logging -->
            <property name="hibernate.show_sql" value="true"/>
            <property name="hibernate.format_sql" value="true"/>
            <property name="hibernate.highlight_sql" value="true"/>

        </properties>

    </persistence-unit>

</persistence>
```

It is a standard JPA configuration, but here we use the specific `org.hibernate.reactive.provider.ReactivePersistenceProvider` as provider to provides ReactiveStreams supports.

Next, add SmallRye related dependencies.

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

In the `DemoApplication`, expose a `Mutiny.SessionFactory` bean.

```java
@Bean
public Mutiny.SessionFactory sessionFactory() {
    return Persistence.createEntityManagerFactory("blogPU")
        .unwrap(Mutiny.SessionFactory.class);
}
```

> Note: you have to update all imports  to use items from package `io.vertx.mutiny`, including `Vertx`, etc.

Replace the `PostRepository` with the following.

```java
@Component
@RequiredArgsConstructor
public class PostRepository {
    private static final Logger LOGGER = Logger.getLogger(PostRepository.class.getName());

    private final Mutiny.SessionFactory sessionFactory;

    public Uni<List<Post>> findAll() {
        CriteriaBuilder cb = this.sessionFactory.getCriteriaBuilder();
        // create query
        CriteriaQuery<Post> query = cb.createQuery(Post.class);
        // set the root class
        Root<Post> root = query.from(Post.class);
        return this.sessionFactory.withSession(session -> session.createQuery(query).getResultList());
    }

    public Uni<List<Post>> findByKeyword(String q, int offset, int limit) {

        CriteriaBuilder cb = this.sessionFactory.getCriteriaBuilder();
        // create query
        CriteriaQuery<Post> query = cb.createQuery(Post.class);
        // set the root class
        Root<Post> root = query.from(Post.class);

        // if keyword is provided
        if (q != null && !q.trim().isEmpty()) {
            query.where(
                cb.or(
                    cb.like(root.get(Post_.title), "%" + q + "%"),
                    cb.like(root.get(Post_.content), "%" + q + "%")
                )
            );
        }
        //perform query
        return this.sessionFactory.withSession(session -> session.createQuery(query)
            .setFirstResult(offset)
            .setMaxResults(limit)
            .getResultList());
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
            return this.sessionFactory.withSession(session -> session.merge(post).onItem().call(session::flush));
        }
    }

    public Uni<Post[]> saveAll(List<Post> data) {
        Post[] array = data.toArray(new Post[0]);
        return this.sessionFactory.withSession(session -> {
            session.persistAll(array);
            session.flush();
            return Uni.createFrom().item(array);
        });
    }

    public Uni<Integer> deleteById(UUID id) {
        CriteriaBuilder cb = this.sessionFactory.getCriteriaBuilder();
        // create delete
        CriteriaDelete<Post> delete = cb.createCriteriaDelete(Post.class);
        // set the root class
        Root<Post> root = delete.from(Post.class);
        // set where clause
        delete.where(cb.equal(root.get(Post_.id), id));
        // perform update
        return this.sessionFactory.withTransaction((session, tx) ->
            session.createQuery(delete).executeUpdate()
        );
    }

    public Uni<Integer> deleteAll() {
        CriteriaBuilder cb = this.sessionFactory.getCriteriaBuilder();
        // create delete
        CriteriaDelete<Post> delete = cb.createCriteriaDelete(Post.class);
        // set the root class
        Root<Post> root = delete.from(Post.class);
        // perform update
        return this.sessionFactory.withTransaction((session, tx) ->
            session.createQuery(delete).executeUpdate()
        );
    }

}
```

It is very similar  to the standard JPA codes, but when using `Mutiny.SessionFactory` to perform the queries, it will return the SmallRye Mutiny specific `Uni`  type instead.

Update the content of `PostsHandler`.

```java
@Component
@RequiredArgsConstructor
class PostsHandler {
    private static final Logger LOGGER = Logger.getLogger(PostsHandler.class.getSimpleName());

    private final PostRepository posts;


    public void all(RoutingContext rc) {
//        var params = rc.queryParams();
//        var q = params.get("q");
//        var limit = params.get("limit") == null ? 10 : Integer.parseInt(params.get("q"));
//        var offset = params.get("offset") == null ? 0 : Integer.parseInt(params.get("offset"));
//        LOGGER.log(Level.INFO, " find by keyword: q={0}, limit={1}, offset={2}", new Object[]{q, limit, offset});
        this.posts.findAll()
            .subscribe()
            .with(
                data -> {
                    LOGGER.log(Level.INFO, "posts data: {0}", data);
                    rc.response().endAndAwait(Json.encode(data));
                },
                rc::fail
            );
    }

    public void get(RoutingContext rc) {
        var params = rc.pathParams();
        var id = params.get("id");
        this.posts.findById(UUID.fromString(id))
            .subscribe()
            .with(
                post -> rc.response().endAndAwait(Json.encode(post)),
                throwable -> rc.fail(404, throwable)
            );
    }

    public void save(RoutingContext rc) {
        //rc.getBodyAsJson().mapTo(PostForm.class)
        var body = rc.getBodyAsJson();
        LOGGER.log(Level.INFO, "request body: {0}", body);
        var form = body.mapTo(CreatePostCommand.class);
        this.posts
            .save(Post.builder()
                .title(form.getTitle())
                .content(form.getContent())
                .build()
            )
            .subscribe()
            .with(
                savedId -> rc.response()
                    .putHeader("Location", "/posts/" + savedId)
                    .setStatusCode(201)
                    .endAndAwait(),
                throwable -> rc.fail(404, throwable)
            );
    }

    public void update(RoutingContext rc) {
        var params = rc.pathParams();
        var id = params.get("id");
        var body = rc.getBodyAsJson();
        LOGGER.log(Level.INFO, "\npath param id: {0}\nrequest body: {1}", new Object[]{id, body});
        var form = body.mapTo(CreatePostCommand.class);
        this.posts.findById(UUID.fromString(id))
            .flatMap(
                post -> {
                    post.setTitle(form.getTitle());
                    post.setContent(form.getContent());

                    return this.posts.save(post);
                }
            )
            .subscribe()
            .with(
                data -> rc.response().setStatusCode(204).endAndAwait(),
                throwable -> rc.fail(404, throwable)
            );
    }

    public void delete(RoutingContext rc) {
        var params = rc.pathParams();
        var id = params.get("id");

        var uuid = UUID.fromString(id);
        this.posts.findById(uuid)
            .flatMap(
                post -> this.posts.deleteById(uuid)
            )
            .subscribe()
            .with(
                data -> rc.response().setStatusCode(204).endAndAwait(),
                throwable -> rc.fail(404, throwable)
            );
    }
}
```

Let's have a look at `MainVerticle` .

```java
//...other imports.
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.vertx.core.AbstractVerticle;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.mutiny.ext.web.Router;
import io.vertx.mutiny.ext.web.handler.BodyHandler;

@Component
@RequiredArgsConstructor
public class MainVerticle extends AbstractVerticle {
    final PostsHandler postHandlers;

    private final static Logger LOGGER = Logger.getLogger(MainVerticle.class.getName());

    static {
        LOGGER.info("Customizing the built-in jackson ObjectMapper...");
        var objectMapper = DatabindCodec.mapper();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS);
        objectMapper.disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS);

        JavaTimeModule module = new JavaTimeModule();
        objectMapper.registerModule(module);
    }

    @Override
    public Uni<Void> asyncStart() {
        LOGGER.log(Level.INFO, "Starting HTTP server...");

        // Configure routes
        var router = routes(postHandlers);

        // Create the HTTP server
        return vertx.createHttpServer()
            // Handle every request using the router
            .requestHandler(router)
            // Start listening
            .listen(8888)
            // Print the port
            .onItem().invoke(() -> LOGGER.info("Http server is listening on http://127.0.0.1:8888"))
            .onFailure().invoke(Throwable::printStackTrace)
            .replaceWithVoid();
    }

    //create routes
    private Router routes(PostsHandler handlers) {

        // Create a Router
        Router router = Router.router(vertx);
        // register BodyHandler globally.
        //router.route().handler(BodyHandler.create());
        router.get("/posts").produces("application/json")
            .handler(handlers::all);
        router.post("/posts").consumes("application/json")
            .handler(BodyHandler.create())
            .handler(handlers::save);
        router.get("/posts/:id").produces("application/json")
            .handler(handlers::get)
            .failureHandler(frc -> frc.response().setStatusCode(404).endAndAwait());
        router.put("/posts/:id").consumes("application/json")
            .handler(BodyHandler.create())
            .handler(handlers::update);
        router.delete("/posts/:id")
            .handler(handlers::delete);

        router.get("/hello").handler(rc -> rc.response().end("Hello from my route"));

        return router;
    }

}

```

The above codes are very similar to the former Spring version.  

The SmallRye Mutiny version `Router` provides some variant methods to accept a simple function instead of the original `RequestHandler`, eg. there is an example to use `respond` method.

```java
//MainVerticle.java
router.get("/posts").produces("application/json")
    .respond(handlers::all);

//PostHandler.java
public Uni<List<Post>> all(RoutingContext rc) {
    return this.posts.findAll();
}
```

To the test the application, add the following dependency into *test* scope.

```xml
<dependency>
    <groupId>io.smallrye.reactive</groupId>
    <artifactId>smallrye-mutiny-vertx-junit5</artifactId>
    <version>${mutiny-vertx.version}</version>
    <scope>test</scope>
</dependency>
```

It provides argument injection for `io.vertx.mutiny.core.Vertx`  and `TestContext` in a test method .

```java
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.http.HttpClientRequest;
import io.vertx.mutiny.core.http.HttpClientResponse;
//other imports

@SpringJUnitConfig(classes = DemoApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(VertxExtension.class)
public class TestMainVerticle {
    private final static Logger LOGGER = Logger.getLogger(TestMainVerticle.class.getName());

    @Autowired
    ApplicationContext context;

    Vertx vertx;

    @BeforeAll
    public void setupAll(VertxTestContext testContext) {
        vertx = context.getBean(Vertx.class);
        var factory = context.getBean(VerticleFactory.class);
        vertx.deployVerticle(factory.prefix() + ":" + MainVerticle.class.getName())
            .subscribe()
            .with(id -> {
                    LOGGER.info("deployed:" + id);
                    testContext.completeNow();
                },
                testContext::failNow
            );
    }

    @Test
    public void testVertx(VertxTestContext testContext) {
        assertThat(vertx).isNotNull();
        testContext.completeNow();
    }


    @Test
    void testGetAll(VertxTestContext testContext) {
        LOGGER.log(Level.INFO, "running test: {0}", "testGetAll");
        var options = new HttpClientOptions()
            .setDefaultPort(8888);
        var client = vertx.createHttpClient(options);

        client.request(HttpMethod.GET, "/posts")
            .flatMap(HttpClientRequest::send)
            .flatMap(HttpClientResponse::body)
            .subscribe()
            .with(buffer ->
                    testContext.verify(
                        () -> {
                            LOGGER.log(Level.INFO, "response buffer: {0}", new Object[]{buffer.toString()});
                            assertThat(buffer.toJsonArray().size()).isGreaterThan(0);
                            testContext.completeNow();
                        }
                    ),
                e -> {
                    LOGGER.log(Level.ALL, "error: {0}", e.getMessage());
                    testContext.failNow(e);
                }
            );
    }


}

```

 Get the [example codes from my github](https://github.com/hantsy/vertx-sandbox/tree/master/mutiy-spring-hibernate).

