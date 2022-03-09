package com.example.demo;

import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = {@Inject})
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
            .onSuccess(
                data -> rc.response().end(Json.encode(data))
            );
    }

    public void get(RoutingContext rc) {
        var params = rc.pathParams();
        var id = params.get("id");
        this.posts.findById(UUID.fromString(id))
            .onSuccess(
                post -> rc.response().end(Json.encode(post))
            )
            .onFailure(
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
        var form = body.mapTo(CreatePostCommand.class);
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
            .onFailure(
                throwable -> rc.fail(404, throwable)
            );

    }

    public void delete(RoutingContext rc) {
        var params = rc.pathParams();
        var id = params.get("id");

        var uuid = UUID.fromString(id);
        this.posts.findById(uuid)
            .compose(
                post -> this.posts.deleteById(uuid)
            )
            .onSuccess(
                data -> rc.response().setStatusCode(204).end()
            )
            .onFailure(
                throwable -> rc.fail(404, throwable)
            );

    }

}
