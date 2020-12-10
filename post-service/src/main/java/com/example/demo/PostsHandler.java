package com.example.demo;

import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

class PostsHandler {
    private static final Logger LOGGER = Logger.getLogger(PostsHandler.class.getSimpleName());

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
        this.posts.findAll()
            .onSuccess(
                data -> rc.response().end(Json.encode(data))
            );
    }

    public void get(RoutingContext rc) {
        var params = rc.pathParams();
        var id = params.get("id");
        this.posts.findById(UUID.fromString(id))
            .onSuccess(post ->
                rc.response().end(Json.encode(post))
            )
            .onFailure(throwable ->
                rc.fail(404, throwable)
            );

    }


    public void save(RoutingContext rc) {
        var body = rc.getBodyAsJson();
        LOGGER.log(Level.INFO, "request body: {0}", body);
        var form = body.mapTo(PostForm.class);
        this.posts.save(Post.of(form.getTitle(), form.getContent()))
            .onSuccess(
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
        LOGGER.log(Level.INFO, "\npath param id: {0}\nrequest body: {1}", new Object[]{id, body});
        var form = body.mapTo(PostForm.class);
        this.posts.findById(UUID.fromString(id))
            .compose(
                post -> {
                    post.setTitle(form.getTitle());
                    post.setContent(form.getContent());

                    return this.posts.update(post);
                }
            )
            .onSuccess(
                data -> rc.response().setStatusCode(204).end()
            )
            .onFailure(throwable ->
                rc.fail(404, throwable)
            );

    }

    public void delete(RoutingContext rc) {
        var params = rc.pathParams();
        var id = params.get("id");

        this.posts.findById(UUID.fromString(id))
            .compose(
                post -> this.posts.deleteById(id)
            )
            .onSuccess(
                data -> rc.response().setStatusCode(204).end()
            )
            .onFailure(
                throwable -> rc.fail(404, throwable)
            );

    }

}
