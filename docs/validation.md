# Exception Handling and Validation Handler in Eclipse Vertx

Vertx's `Future` includes some hooks when the execution is done.

* `onComplete` will in invoked when the execution is completed, either it is succeeded or failed.
* `onSuccess` handles the successful result.
* `onFailure` catches the exception thrown in the execution.

Let's explore how to handle the exceptions in the former example application. 

Assume retrieving `Post` via a none-existing id, throw a `PostNotFoundException` instead of returning the correct result. 

Declare a `PostNotFoundException` .

```java
public class PostNotFoundException extends RuntimeException {
    public PostNotFoundException(UUID id) {
        super("Post id: " + id + " was not found. ");
    }
}
```

In the `PostRepository`,  change the content of `findById` like the following.

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

In the  `PostsHandler`, the `get` method handles */posts/:id* route like this.

```java
public void get(RoutingContext rc) {
    var params = rc.pathParams();
    var id = params.get("id");
    this.posts.findById(UUID.fromString(id))
        .onSuccess(
            post -> rc.response().end(Json.encode(post))
        )
        .onFailure(
            throwable -> rc.fail(throwable)
        );

}
```

In the `onFailure` hook, use `RoutingContext.fail` to transit the exception state in route.  

Let's review the router definition in the */posts/:id* route.  

```java
router.get("/posts/:id")
    .produces("application/json")
    .handler(handlers::get)
    .failureHandler(frc -> {
        Throwable failure = frc.failure();
        if (failure instanceof PostNotFoundException) {
            frc.response().setStatusCode(404).end();
        }
        frc.response().setStatusCode(500).setStatusMessage("Server internal error:" + failure.getMessage()).end();
    });
```



There is a failure handler to handle exceptions in details.

In the above `PostsHandler` example, there is a `fail`  alternative method accepts a status code parameter.  If there is there is no failure handler in the router definition, it will send the the code as HTTP Status code to the client response.

Check the [source codes from my Github](https://github.com/hantsy/vertx-sandbox/tree/master/post-service).

For those cases which include a request body, such as *create a new post* , the request body should be validated to ensure it satisfies the requirements.

The validation progress can be done by a validation handler, similar to other request handlers, you can chain the handlers in your router definition.

```java
router.post("/posts").consumes("application/json")
    .handler(BodyHandler.create())
    .handler(validation)
    .handler(handlers::save)
    .failureHandler(validationFailureHandler);
```

The `BodyHandler` is used to deserialize the request body, then validate the decoded body via a validation handler. If the validation is successful, call `handlers::save` to save the post data.  A failure handler is declared in the last position to handle the possible validation exception thrown in the execution.

Vertx supports rich validation rule definitions based on Json Schema specification. 

Add the following dependency into your *pom.xml*.

```xml
<dependency>
    <groupId>io.vertx</groupId>
    <artifactId>vertx-json-schema</artifactId>
</dependency>
```

The following is an example of defining a validation handler to validate the request body of *creating a new post*.

```java
SchemaRouter schemaRouter = SchemaRouter.create(vertx, new SchemaRouterOptions());
SchemaParser schemaParser = SchemaParser.createDraft201909SchemaParser(schemaRouter);

ObjectSchemaBuilder bodySchemaBuilder = objectSchema()
    .requiredProperty("title", stringSchema().with(minLength(5)).with(maxLength(100)))
    .requiredProperty("content", stringSchema().with(minLength(10)).with(maxLength(2000)));

ValidationHandler validation = ValidationHandler.newInstance(
    ValidationHandler
    .builder(schemaParser)
    //.queryParameter(param("parameterName", intSchema()))
    //.pathParameter(param("pathParam", numberSchema()))
    .body(Bodies.json(bodySchemaBuilder))
    //.body(Bodies.formUrlEncoded(bodySchemaBuilder))
    .predicate(RequestPredicate.BODY_REQUIRED)
    .build()
);
```

The above is an example of using RxJava 3 Validation binding APIs, but there is [an issue](https://github.com/vert-x3/vertx-web/issues/1822), you have to wrap the instance to create a RxJava3 specific validation handler.

 When request body is failed to validate, it will throw a  `BodyProcessorException`. The failure handler  is used to handle the exception and send desired status to the response.

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

Check the [source codes from my Github](https://github.com/hantsy/vertx-sandbox/tree/master/rxjava3).

