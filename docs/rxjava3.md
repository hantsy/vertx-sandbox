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
import io.vertx.rxjava3.ext.web.validation.ValidationHandler;
import io.vertx.rxjava3.json.schema.SchemaParser;
import io.vertx.rxjava3.json.schema.SchemaRouter;
import io.vertx.rxjava3.pgclient.PgPool;
// other imports

@Slf4j
public class MainVerticle extends AbstractVerticle {

    @Override
    public Completable rxStart() {}
    
     //create routes
    private Router routes(PostsHandler handlers) {}
    
    private PgPool pgPool() {}
}
```

As you see, we import all essential classes from `io.vertx.rxjava3`, and use RxJava 3 API instead of  the Vertx built-in  `Future` and `Promise`.  In this implementation, we override the `rxStart` which return a RxJava `Completable`.

Ok, let's  move to the `PostRepository`  class.  

```java
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.pgclient.PgPool;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.SqlResult;
import io.vertx.rxjava3.sqlclient.Tuple;
// other imports...

@Slf4j
public class PostRepository {

    private static Function<Row, Post> MAPPER = (row) ->
        Post.of(
            row.getUUID("id"),
            row.getString("title"),
            row.getString("content"),
            row.getLocalDateTime("created_at")
        );


    private final PgPool client;

    private PostRepository(PgPool _client) {
        this.client = _client;
    }

    //factory method
    public static PostRepository create(PgPool client) {
        return new PostRepository(client);
    }

    public Flowable<Post> findAll() {
        return this.client
            .query("SELECT * FROM posts")
            .rxExecute()
            .flattenAsFlowable(
                rows -> StreamSupport.stream(rows.spliterator(), false)
                    .map(MAPPER)
                    .collect(Collectors.toList())
            );
    }


    public Single<Post> findById(UUID id) {
        Objects.requireNonNull(id, "id can not be null");
        return client.preparedQuery("SELECT * FROM posts WHERE id=$1").rxExecute(Tuple.of(id))
            .map(RowSet::iterator)
            .flatMap(iterator -> iterator.hasNext() ? Single.just(MAPPER.apply(iterator.next())) : Single.error(new PostNotFoundException(id)));
    }

    public Single<UUID> save(Post data) {
        return client.preparedQuery("INSERT INTO posts(title, content) VALUES ($1, $2) RETURNING (id)")
            .rxExecute(Tuple.of(data.getTitle(), data.getContent()))
            .map(rs -> rs.iterator().next().getUUID("id"));
    }

    public Single<Integer> saveAll(List<Post> data) {
        var tuples = data.stream()
            .map(d -> Tuple.of(d.getTitle(), d.getContent()))
            .collect(Collectors.toList());

        return client.preparedQuery("INSERT INTO posts (title, content) VALUES ($1, $2)")
            .rxExecuteBatch(tuples)
            .map(SqlResult::rowCount);
    }

    public Single<Integer> update(Post data) {
        return client.preparedQuery("UPDATE posts SET title=$1, content=$2 WHERE id=$3")
            .rxExecute(Tuple.of(data.getTitle(), data.getContent(), data.getId()))
            .map(SqlResult::rowCount);
    }

    public Single<Integer> deleteAll() {
        return client.query("DELETE FROM posts").rxExecute()
            .map(SqlResult::rowCount);
    }

    public Single<Integer> deleteById(UUID id) {
        Objects.requireNonNull(id, "id can not be null");
        return client.preparedQuery("DELETE FROM posts WHERE id=$1").rxExecute(Tuple.of(id))
            .map(SqlResult::rowCount);
    }

}
```



In this class, we use a RxJava 3 API based `PgPool` instead which wraps the original Vertx `PgPool` and add extra RxJava 3 APIs support. All methods are similar to the former Vertx version, here we use a `rxExecute` method to execute the SQL query and the returned result is switched to the RxJava 3 world.

Let's have a look at the `PostsHandler`.

```java
import io.vertx.rxjava3.ext.web.RoutingContext;
//other imports

@Slf4j
class PostsHandler {

    PostRepository posts;

    private PostsHandler(PostRepository _posts) {
        this.posts = _posts;
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
            .subscribe(
                data -> rc.response().end(Json.encode(data))
            );
    }

    public void get(RoutingContext rc) throws PostNotFoundException {
        var params = rc.pathParams();
        var id = params.get("id");
        var uuid = UUID.fromString(id);
        this.posts.findById(uuid)
            .subscribe(
                post -> rc.response().end(Json.encode(post)),
                throwable -> rc.fail(404, throwable)
            );

    }


    public void save(RoutingContext rc) {
        //rc.getBodyAsJson().mapTo(PostForm.class)
        var body = rc.getBodyAsJson();
        log.info("request body: {0}", body);
        var form = body.mapTo(CreatePostCommand.class);
        this.posts
            .save(Post.builder()
                .title(form.getTitle())
                .content(form.getContent())
                .build()
            )
            .subscribe(
                savedId -> rc.response()
                    .putHeader("Location", "/posts/" + savedId)
                    .setStatusCode(201)
                    .end()

            );
    }

    public void update(RoutingContext rc) {
        var params = rc.pathParams();
        var id = params.get("id");
        var body = rc.getBodyAsJson();
        log.info("\npath param id: {}\nrequest body: {}", id, body);
        var form = body.mapTo(CreatePostCommand.class);
        this.posts.findById(UUID.fromString(id))
            .flatMap(
                post -> {
                    post.setTitle(form.getTitle());
                    post.setContent(form.getContent());

                    return this.posts.update(post);
                }
            )
            .subscribe(
                data -> rc.response().setStatusCode(204).end(),
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
            .subscribe(
                data -> rc.response().setStatusCode(204).end(),
                throwable -> rc.fail(404, throwable)
            );

    }

}
```



In the `subscribe` method, use the RxJava 3 specific `RoutingContext` to send response.

Refactor the `DataInitializer` to use the RxJava 3 API bindings.

```java
@Slf4j
public class DataInitializer {

    private PgPool client;

    public DataInitializer(PgPool client) {
        this.client = client;
    }

    public static DataInitializer create(PgPool client) {
        return new DataInitializer(client);
    }

    public void run() {
        log.info("Data initialization is starting...");

        Tuple first = Tuple.of("Hello Quarkus", "My first post of Quarkus");
        Tuple second = Tuple.of("Hello Again, Quarkus", "My second post of Quarkus");

        client
            .rxWithTransaction(
                (SqlConnection tx) -> tx.query("DELETE FROM posts").rxExecute()
                    .flatMap(result -> tx.preparedQuery("INSERT INTO posts (title, content) VALUES ($1, $2)").rxExecuteBatch(List.of(first, second)))
                    .toMaybe()
            )
            .flatMapSingle(d -> client.query("SELECT * FROM posts").rxExecute())
            .subscribe(
                (data) -> {
                    data.forEach(row -> {
                        log.info("saved row: {}", row.toJson());
                    });
                },
                err -> log.warn("failed to initializing: {}", err.getMessage())
            );
    }
}

```



The complete codes of the `rxStart` method in the `MainVerticle` class.

```java
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
        // to Completable
        .ignoreElement()
        ;
}
```

Now you can run the application. 

```bash
mvn clean compile exec:java
```

Get the [source codes from my github](https://github.com/hantsy/vertx-sandbox/tree/master/rxjava3),  if you are still using the RxJava 2, there also includes a [RxJava 2 version](https://github.com/hantsy/vertx-sandbox/tree/master/rxjava2).

