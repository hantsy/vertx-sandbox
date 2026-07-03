# Building RESTful APIs with Eclipse Vertx  and RxJava 3 API Bindings

Eclipse Vertx has a great codegen mechanism to bring its *Event Loop* based asynchronous programming model to diverse platforms, including other asynchronous  libraries, such as [RxJava2/3](https://github.com/ReactiveX/RxJava) and [SmallRye Mutiny](https://smallrye.io/smallrye-mutiny/) and different languages,  such as Kotlin, Kotlin Coroutines, and even Node/Typescript, PHP etc.

In this post, we will refactor the RESTful APIs we have done in [the last post](https://itnext.io/building-restful-apis-with-eclipse-vertx-4ce89d8eeb74) and reimplement it with RxJava 3. RxJava 3 fully implements [Reactive Steams specification](http://www.reactive-streams.org/).

Open your browser, go to [Vertx Starter](https://start.vertx.io), create a Vertx project skeleton.  Do not forget to add *RxJava 3* to the dependencies.

If you are working on an existing project, add the *rxjava3* dependency directly.

```xml
<dependency>
    <groupId>io.vertx</groupId>
    <artifactId>vertx-rx-java3</artifactId>
</dependency>
```

Import the project source codes into your IDE, eg.  Intellij IDEA.

Firstly let's have a  look at `MainVerticle`.

```java
import io.vertx.rxjava3.core.AbstractVerticle;
import io.vertx.rxjava3.ext.web.Router;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import io.vertx.rxjava3.sqlclient.Pool;
// other imports

@Slf4j
public class MainVerticle extends AbstractVerticle {

    @Override
    public Completable rxStart() {}
    
     //create routes
    private Router routes(PostsHandler handlers) {}
    
    private Pool pgPool() {}
}
```

As you see, we import all essential classes from `io.vertx.rxjava3`, and use RxJava 3 API instead of  the Vertx built-in  `Future` and `Promise`.  In this implementation, we override the `rxStart` which return a RxJava `Completable`.

Ok, let's  move to the `PostRepository`  class.  

```java
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.sqlclient.*;
// other imports...

@Slf4j
public class PostRepository {

    private static final Function<Row, Post> MAPPER = (Row row) ->
        new Post(
            row.getUUID("id"),
            row.getString("title"),
            row.getString("content"),
            row.getLocalDateTime("created_at")
        );


    private final Pool client;

    private PostRepository(Pool pgClient) {
        this.client = pgClient;
    }

    //factory method
    public static PostRepository create(Pool client) {
        return new PostRepository(client);
    }

    public Flowable<Post> findAll() {
        return this.client
            .query("SELECT * FROM posts")
            .rxExecute()
            .flattenAsFlowable(
                rows -> StreamSupport.stream(rows.spliterator(), false)
                    .map(MAPPER)
                    .toList()
            );
    }


    public Single<Post> findById(UUID id) {
        Objects.requireNonNull(id, "id can not be null");
        return client.preparedQuery("SELECT * FROM posts WHERE id=$1")
            .rxExecute(Tuple.of(id))
            .map(RowSet::iterator)
            .flatMap(iterator -> iterator.hasNext() ?
                Single.just(MAPPER.apply(iterator.next())) :
                Single.error(new PostNotFoundException(id))
            );
    }

    public Single<UUID> save(Post data) {
        String sql = "INSERT INTO posts(title, content) VALUES ($1, $2) RETURNING (id)";
        return client.preparedQuery(sql)
            .rxExecute(Tuple.of(data.title(), data.content()))
            .map(rs -> rs.iterator().next().getUUID("id"));
    }

    public Single<Integer> saveAll(List<Post> data) {
        var tuples = data.stream()
            .map(d -> Tuple.of(d.title(), d.content()))
            .collect(Collectors.toList());

        String sql = "INSERT INTO posts (title, content) VALUES ($1, $2)";
        return client.preparedQuery(sql)
            .rxExecuteBatch(tuples)
            .map(SqlResult::rowCount);
    }

    public Single<Integer> update(Post data) {
        String sql = "UPDATE posts SET title=$1, content=$2 WHERE id=$3";
        return client.preparedQuery(sql)
            .rxExecute(Tuple.of(data.title(), data.content(), data.id()))
            .map(SqlResult::rowCount);
    }

    public Single<Integer> deleteAll() {
        String sql = "DELETE FROM posts";
        return client.query(sql).rxExecute()
            .map(SqlResult::rowCount);
    }

    public Single<Integer> deleteById(UUID id) {
        Objects.requireNonNull(id, "id can not be null");
        String sql = "DELETE FROM posts WHERE id=$1";
        return client.preparedQuery(sql).rxExecute(Tuple.of(id))
            .map(SqlResult::rowCount);
    }

}
```



In this class, we use a RxJava 3 API based `Pool` (created via `PgBuilder.pool()`) which wraps the original Vertx SQL client and adds extra RxJava 3 API support. All methods are similar to the former Vertx version, here we use a `rxExecute` method to execute the SQL query and the returned result is switched to the RxJava 3 world.

Let's have a look at the `PostsHandler`.

```java
import io.vertx.rxjava3.ext.web.RoutingContext;
//other imports

@Slf4j
class PostsHandler {
    private static final Logger log = LoggerFactory.getLogger(PostsHandler.class);
    private final PostRepository posts;

    private PostsHandler(PostRepository postRepository) {
        this.posts = postRepository;
    }

    //factory method
    public static PostsHandler create(PostRepository posts) {
        return new PostsHandler(posts);
    }

    public void all(RoutingContext rc) {
//        var params = rc.queryParams();
//        var q = params.get("q");
//        var limit = params.get("limit") == null ? 10 : Integer.parseInt(params.get("q"));
//        var offset = params.get("offset") == null ? 0 : Integer.parseInt(params.get("offset"));
//        LOGGER.log(Level.INFO, " find by keyword: q={0}, limit={1}, offset={2}", new Object[]{q, limit, offset});
        this.posts.findAll().takeLast(10).toList()
            .subscribe(data -> rc.response().rxSend(Json.encode(data)));
    }

    public void get(RoutingContext rc) throws PostNotFoundException {
        var params = rc.pathParams();
        var id = params.get("id");
        var uuid = UUID.fromString(id);
        this.posts.findById(uuid)
            .subscribe(
                post -> rc.response().rxSend(Json.encode(post)),
                error -> rc.fail(404, new PostNotFoundException(uuid))
            );
    }


    public void save(RoutingContext rc) {
        //rc.getBodyAsJson().mapTo(PostForm.class)
        var body = rc.body().asJsonObject();
        log.info("request body: {0}", body);
        var form = body.mapTo(CreatePostCommand.class);
        this.posts
            .save(new Post(null, form.title(), form.content(), null))
            .subscribe(savedId -> rc.response()
                .putHeader("Location", "/posts/" + savedId)
                .setStatusCode(201)
                .rxEnd()
            );
    }

    public void update(RoutingContext rc) {
        var params = rc.pathParams();
        var id = params.get("id");
        var body = rc.body().asJsonObject();
        log.info("\npath param id: {}\nrequest body: {}", id, body);
        var form = body.mapTo(CreatePostCommand.class);

        this.posts.findById(UUID.fromString(id))
            .flatMap(post -> {
                    var toUpdated = new Post(post.id(), form.title(), form.content(), null);
                    return this.posts.update(toUpdated);
                }
            )
            .subscribe(data -> rc.response().setStatusCode(204).rxEnd());
    }

    public void delete(RoutingContext rc) {
        var params = rc.pathParams();
        var id = params.get("id");
        var uuid = UUID.fromString(id);
        this.posts.findById(uuid)
            .flatMap(post -> this.posts.deleteById(uuid))
            .subscribe(data -> rc.response().setStatusCode(204).rxEnd(),
                error -> rc.fail(404, error)
            );
    }

}
```



In the `subscribe` method, use the RxJava 3 specific `RoutingContext` to send response.

Refactor the `DataInitializer` to use the RxJava 3 API bindings.

```java
@Slf4j
public class DataInitializer {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final Pool client;

    public DataInitializer(Pool client) {
        this.client = client;
    }

    public static DataInitializer create(Pool client) {
        return new DataInitializer(client);
    }

    public void run() {
        log.info("Data initialization is starting...");

        Tuple first = Tuple.of("Hello Vertx", "My first post of Vertx");
        Tuple second = Tuple.of("Hello Again, Vertx", "My second post of Vertx");

        var latch = new CountDownLatch(1);

        client
            .rxWithTransaction(
                (SqlConnection tx) -> tx.query("DELETE FROM posts").rxExecute()
                    .flatMap(result -> tx.preparedQuery("INSERT INTO posts (title, content) VALUES ($1, $2)").rxExecuteBatch(List.of(first, second)))
                    .toMaybe()
            )
            .flatMapSingle(d -> client.query("SELECT * FROM posts").rxExecute())
            .doOnTerminate(latch::countDown)
            .subscribe(
                (RowSet<Row> data) -> {
                    data.forEach(row -> log.info("saved row: {}", row.toJson()));
                    log.debug("Data initialization is completed successfully...");
                },
                err -> {
                    log.warn("failed to initializing: {}", err.getMessage());
                }
            );

        try {
            latch.await(1000, TimeUnit.MILLISECONDS);
            log.info("Data initialization is done...");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}

```



The complete codes of the `rxStart` method in the `MainVerticle` class.

```java
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
```

Now you can run the application. 

```bash
mvn clean compile exec:java
```

Get the [source codes from my github](https://github.com/hantsy/vertx-sandbox/tree/master/rxjava3),  if you are still using the RxJava 2, there also includes a [RxJava 2 version](https://github.com/hantsy/vertx-sandbox/tree/master/rxjava2).
