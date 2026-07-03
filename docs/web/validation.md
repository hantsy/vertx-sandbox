# Exception Handling and Validation Handler in Eclipse Vert.x

Vert.x's `Future` includes several hooks when the execution is done:

* `onComplete` — invoked when the execution completes, either succeeded or failed.
* `onSuccess` — handles the successful result.
* `onFailure` — catches the exception thrown in the execution.

Let's explore exception handling using the [web module](../start/rest.md) as an example.

The project uses **Vert.x 5.1.3**, **Java 25**, and the **Launcher** approach (`io.vertx.launcher.application.VertxApplication`). It uses `Pool`/`PgBuilder` for PostgreSQL access and `vertx-web-validation` for request body validation.

## Exception Handling with Failure Handler

Assume retrieving a `Post` via a non-existing id throws a `PostNotFoundException`:

```java
public class PostNotFoundException extends RuntimeException {
    public PostNotFoundException(UUID id) {
        super("Post id: " + id + " was not found. ");
    }
}
```

In the `PostRepository.findById` method, the exception is thrown when no row is found:

```java
public Future<Post> findById(UUID id) {
    Objects.requireNonNull(id, "id can not be null");
    return client.preparedQuery("SELECT * FROM posts WHERE id=$1")
        .execute(Tuple.of(id))
        .map(RowSet::iterator)
        .map(iterator -> {
            if (iterator.hasNext()) {
                return MAPPER.apply(iterator.next());
            }
            throw new PostNotFoundException(id);
        });
}
```

In the `PostsHandler`, use `RoutingContext.fail()` to propagate the exception to a failure handler:

```java
public void get(RoutingContext rc) {
    var params = rc.pathParams();
    var id = params.get("id");
    this.posts.findById(UUID.fromString(id))
        .onSuccess(post -> rc.response().end(Json.encode(post)))
        .onFailure(rc::fail);
}
```

In the router definition, a `failureHandler` catches the exception and returns the appropriate HTTP status code:

```java
router.get("/posts/:id").produces("application/json")
    .handler(handlers::get)
    .failureHandler(frc -> {
        Throwable failure = frc.failure();
        if (failure instanceof PostNotFoundException) {
            frc.response().setStatusCode(404).end();
        } else {
            frc.response().setStatusCode(500)
                .setStatusMessage("Server internal error:" + failure.getMessage()).end();
        }
    });
```

If there is no failure handler defined, Vert.x will use the status code passed to `routingContext.fail(statusCode)` as the HTTP response status.

Check the [source codes from my Github](https://github.com/hantsy/vertx-sandbox/tree/master/web).

## Request Body Validation

For endpoints with a request body, such as creating a new post, the body should be validated to ensure it satisfies the requirements. Vert.x supports rich validation rule definitions based on JSON Schema via `vertx-web-validation`.

Add the following dependency into your *pom.xml*:

```xml
<dependency>
    <groupId>io.vertx</groupId>
    <artifactId>vertx-web-validation</artifactId>
</dependency>
```

(`vertx-web-validation` is provided via `vertx-stack-depchain` in the dependency management.)

### Router Setup with Validation

```java
router.post("/posts").consumes("application/json")
    .handler(BodyHandler.create())
    .handler(validation)
    .handler(handlers::save)
    .failureHandler(validationFailureHandler);
```

1. `BodyHandler` deserializes the request body.
2. The **validation handler** validates the decoded body against the defined JSON Schema rules.
3. If valid, `handlers::save` processes the request.
4. If validation fails, `validationFailureHandler` returns the error response.

### Defining a Validation Handler

```java
SchemaRouter schemaRouter = SchemaRouter.create(vertx, new SchemaRouterOptions());
SchemaParser schemaParser = SchemaParser.createDraft201909SchemaParser(schemaRouter);

ObjectSchemaBuilder bodySchemaBuilder = objectSchema()
    .requiredProperty("title", stringSchema().with(minLength(5)).with(maxLength(100)))
    .requiredProperty("content", stringSchema().with(minLength(10)).with(maxLength(2000)));

ValidationHandler validation = ValidationHandler.newInstance(
    ValidationHandler
        .builder(schemaParser)
        .body(Bodies.json(bodySchemaBuilder))
        .predicate(RequestPredicate.BODY_REQUIRED)
        .build()
);
```

This validates that the request body contains a JSON object with a `title` (5-100 characters) and `content` (10-2000 characters).

### Validation Failure Handler

When validation fails, a `BodyProcessorException` is thrown. The failure handler catches it and returns a 400 response:

```java
Handler<RoutingContext> validationFailureHandler = (RoutingContext rc) -> {
    if (rc.failure() instanceof BodyProcessorException exception) {
        rc.response()
            .setStatusCode(400)
            .end("validation failed.");
        //.end(exception.toJson().encode());
    }
};
```

Check the [source codes from my Github](https://github.com/hantsy/vertx-sandbox/tree/master/web).

