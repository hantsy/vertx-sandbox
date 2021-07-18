package com.example.demo;

import io.vertx.core.json.Json;
import io.vertx.mutiny.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

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

/*

    public Uni<List<Post>> all(RoutingContext rc) {
//        var params = rc.queryParams();
//        var q = params.get("q");
//        var limit = params.get("limit") == null ? 10 : Integer.parseInt(params.get("q"));
//        var offset = params.get("offset") == null ? 0 : Integer.parseInt(params.get("offset"));
//        LOGGER.log(Level.INFO, " find by keyword: q={0}, limit={1}, offset={2}", new Object[]{q, limit, offset});
        return this.posts.findAll();
    }

    public Uni<Post> get(RoutingContext rc) {
        var params = rc.pathParams();
        var id = params.get("id");
        return this.posts.findById(UUID.fromString(id));
    }

    public Uni<Post> save(RoutingContext rc) {
        //rc.getBodyAsJson().mapTo(PostForm.class)
        var body = rc.getBodyAsJson();
        LOGGER.log(Level.INFO, "request body: {0}", body);
        var form = body.mapTo(CreatePostCommand.class);
        return this.posts
            .save(Post.builder()
                .title(form.getTitle())
                .content(form.getContent())
                .build()
            )
            .onItem().invoke(saved -> rc.response()
                .putHeader("Location", "/posts/" + saved.getId())
                .setStatusCode(201).end()
            )
            .onFailure().invoke(rc::fail);
    }

    public Uni<Post> update(RoutingContext rc) {
        var params = rc.pathParams();
        var id = params.get("id");
        var body = rc.getBodyAsJson();
        LOGGER.log(Level.INFO, "\npath param id: {0}\nrequest body: {1}", new Object[]{id, body});
        var form = body.mapTo(CreatePostCommand.class);
        return this.posts.findById(UUID.fromString(id))
            .flatMap(
                post -> {
                    post.setTitle(form.getTitle());
                    post.setContent(form.getContent());

                    return this.posts.save(post);
                }
            )
            .onItem().invoke(updated -> rc.response()
                .setStatusCode(204).end()
            )
            .onFailure().invoke(rc::fail);
    }

    public Uni<Integer> delete(RoutingContext rc) {
        var params = rc.pathParams();
        var id = params.get("id");

        var uuid = UUID.fromString(id);
        return this.posts.findById(uuid)
            .flatMap(
                post -> this.posts.deleteById(uuid)
            )
            .onItem().invoke(deleted -> rc.response()
                .setStatusCode(204).end()
            )
            .onFailure().invoke(rc::fail);
    }
*/

}
