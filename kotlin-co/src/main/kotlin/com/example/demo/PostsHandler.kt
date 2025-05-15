package com.example.demo

import io.vertx.core.json.Json
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.coAwait
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

class PostsHandler(private val posts: PostRepository) {
    companion object {
        private val LOGGER = Logger.getLogger(PostsHandler::class.java.name)
    }

    suspend fun all(rc: RoutingContext) {
        LOGGER.log(Level.ALL, "handling /posts endpoint")
//        var params = rc.queryParams();
//        var q = params.get("q");
//        var limit = params.get("limit") == null ? 10 : Integer.parseInt(params.get("q"));
//        var offset = params.get("offset") == null ? 0 : Integer.parseInt(params.get("offset"));
//        LOGGER.log(Level.INFO, " find by keyword: q={0}, limit={1}, offset={2}", new Object[]{q, limit, offset});
        val data = posts.findAll()
        rc.response().end(Json.encode(data)).coAwait()
    }

    suspend fun getById(rc: RoutingContext) {
        val id = rc.pathParam("id") ?: throw IllegalArgumentException("Path param id is required")
        val uuid = UUID.fromString(id)
        val data = posts.findById(uuid)
        rc.response().end(Json.encode(data)).coAwait()
    }

    suspend fun save(rc: RoutingContext) {
        //rc.getBodyAsJson().mapTo(PostForm.class)
        val body = rc.body().asJsonObject()
        LOGGER.log(Level.INFO, "request body: {0}", body)
        val (title, content) = body.mapTo(CreatePostCommand::class.java)
        val savedId = posts.save(Post(title = title, content = content))
        rc.response()
            .putHeader("Location", "/posts/$savedId")
            .setStatusCode(201)
            .end()
            .coAwait()
    }

    suspend fun update(rc: RoutingContext) {
        val id = rc.pathParam("id") ?: throw IllegalArgumentException("Path param id is required")
        val uuid = UUID.fromString(id)
        val body = rc.body().asJsonObject()
        LOGGER.log(Level.INFO, "\npath param id: {0}\nrequest body: {1}", arrayOf(id, body))
        val (title, content) = body.mapTo(UpdatePostCommand::class.java)
        val existing: Post = posts.findById(uuid)
        val data: Post = existing.apply {
            this.title = title
            this.content = content
        }
        posts.update(data)
        rc.response().setStatusCode(204).end().coAwait()
    }

    suspend fun delete(rc: RoutingContext) {
        val id = rc.pathParam("id") ?: throw IllegalArgumentException("Path param id is required")
        val uuid = UUID.fromString(id)
        val existing = posts.deleteById(uuid)
        if (existing > 0) {
            rc.response().setStatusCode(204).end().coAwait()
        } else {
            rc.fail(404, PostNotFoundException(uuid))
        }
    }
}
