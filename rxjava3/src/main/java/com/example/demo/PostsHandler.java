package com.example.demo;

import io.vertx.core.json.Json;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

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
