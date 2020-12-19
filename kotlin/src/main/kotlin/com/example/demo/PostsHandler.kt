package com.example.demo

import io.vertx.core.json.Json
import io.vertx.ext.web.RoutingContext
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

class PostsHandler(val posts: PostRepository) {
    fun all(rc: RoutingContext) {
//        var params = rc.queryParams();
//        var q = params.get("q");
//        var limit = params.get("limit") == null ? 10 : Integer.parseInt(params.get("q"));
//        var offset = params.get("offset") == null ? 0 : Integer.parseInt(params.get("offset"));
//        LOGGER.log(Level.INFO, " find by keyword: q={0}, limit={1}, offset={2}", new Object[]{q, limit, offset});
        posts.findAll()
            .onSuccess {
                rc.response().end(Json.encode(it))
            }

    }

    fun getById(rc: RoutingContext) {
        val params = rc.pathParams()
        val id = params["id"]
        posts.findById(UUID.fromString(id))
            .onSuccess { rc.response().end(Json.encode(it)) }
            .onFailure { rc.fail(404, it) }
    }

    fun save(rc: RoutingContext) {
        //rc.getBodyAsJson().mapTo(PostForm.class)
        val body = rc.bodyAsJson
        LOGGER.log(Level.INFO, "request body: {0}", body)
        val (title, content) = body.mapTo(PostForm::class.java)
        posts.save(Post(title = title, content = content))
            .onSuccess { savedId: UUID ->
                rc.response()
                    .putHeader("Location", "/posts/$savedId")
                    .setStatusCode(201)
                    .end()
            }
    }

    fun update(rc: RoutingContext) {
        val params = rc.pathParams()
        val id = params["id"]
        val body = rc.bodyAsJson
        LOGGER.log(Level.INFO, "\npath param id: {0}\nrequest body: {1}", arrayOf(id, body))
        var (title, content) = body.mapTo(PostForm::class.java)
        posts.findById(UUID.fromString(id))
            .flatMap { post: Post ->
                post.apply {
                    title = title
                    content = content
                }
                posts.update(post)
            }
            .onSuccess { rc.response().setStatusCode(204).end() }
            .onFailure { rc.fail(404, it) }
    }

    fun delete(rc: RoutingContext) {
        val params = rc.pathParams()
        val id = params["id"]
        val uuid = UUID.fromString(id)
        posts.findById(uuid)
            .flatMap { posts.deleteById(uuid) }
            .onSuccess { rc.response().setStatusCode(204).end() }
            .onFailure { rc.fail(404, it) }
    }

    companion object {
        private val LOGGER = Logger.getLogger(PostsHandler::class.java.simpleName)
    }
}
