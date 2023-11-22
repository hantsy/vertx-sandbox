package com.example.demo;

import io.vertx.core.json.Json;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

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
        var body = rc.body().asJsonObject();
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
        var body = rc.body().asJsonObject();
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
