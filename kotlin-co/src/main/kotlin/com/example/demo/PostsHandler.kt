package com.example.demo

import io.vertx.core.json.Json
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.await
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

class PostsHandler(private val posts: PostRepository) {
    suspend fun all(rc: RoutingContext) {
//        var params = rc.queryParams();
//        var q = params.get("q");
//        var limit = params.get("limit") == null ? 10 : Integer.parseInt(params.get("q"));
//        var offset = params.get("offset") == null ? 0 : Integer.parseInt(params.get("offset"));
//        LOGGER.log(Level.INFO, " find by keyword: q={0}, limit={1}, offset={2}", new Object[]{q, limit, offset});
        val data = posts.findAll()
        rc.response().end(Json.encode(data)).await()
    }

    suspend fun getById(rc: RoutingContext) {
        val params = rc.pathParams()
        val id = params["id"]
        val uuid = UUID.fromString(id)
        val data = posts.findById(uuid)
        rc.response().end(Json.encode(data)).await()
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
            .await()

    }

    suspend fun update(rc: RoutingContext) {
        val params = rc.pathParams()
        val id = params["id"]
        val uuid = UUID.fromString(id)
        val body = rc.body().asJsonObject()
        LOGGER.log(Level.INFO, "\npath param id: {0}\nrequest body: {1}", arrayOf(id, body))
        var (title, content) = body.mapTo(UpdatePostCommand::class.java)

        var existing: Post? = posts.findById(uuid)
        if (existing != null) {
            val data: Post = existing.apply {
                title = title
                content = content
            }
            posts.update(data)
            rc.response().setStatusCode(204).end().await()
        } else {
            rc.fail(404, PostNotFoundException(uuid))
        }
    }

    suspend fun delete(rc: RoutingContext) {
        val params = rc.pathParams()
        val id = params["id"]
        val uuid = UUID.fromString(id)
        val existing = posts.findById(uuid)
        if (existing != null) {
            rc.response().setStatusCode(204).end().await()
        } else {
            rc.fail(404, PostNotFoundException(uuid))
        }
    }

    companion object {
        private val LOGGER = Logger.getLogger(PostsHandler::class.java.simpleName)
    }
}
