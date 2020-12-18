package com.example.demo

import io.vertx.core.Future
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.Tuple
import java.util.*
import java.util.function.Function
import java.util.logging.Logger
import java.util.stream.Collectors
import java.util.stream.StreamSupport

class PostRepository(private val client: PgPool) {

    fun findAll(): Future<List<Post>> {
        return client.query("SELECT * FROM posts ORDER BY id ASC")
            .execute()
            .map { rs: RowSet<Row?> ->
                StreamSupport.stream(rs.spliterator(), false)
                    .map { MAPPER.apply(it!!) }
                    .collect(Collectors.toList())
            }
    }

    fun findById(id: UUID): Future<Post> {
        Objects.requireNonNull(id, "id can not be null")
        return client.preparedQuery("SELECT * FROM posts WHERE id=$1").execute(Tuple.of(id))
            .map { it.iterator() }
            .map {
                if (it.hasNext()) MAPPER.apply(it.next()) else null
            }
            .map { Optional.ofNullable(it) }
            .map { it.orElseThrow<RuntimeException> { PostNotFoundException(id) } }
    }

    fun save(data: Post): Future<UUID> {
        return client.preparedQuery("INSERT INTO posts(title, content) VALUES ($1, $2) RETURNING (id)")
            .execute(Tuple.of(data.title, data.content))
            .map { it.iterator().next().getUUID("id") }
    }

    fun saveAll(data: List<Post>): Future<Int> {
        val tuples = data.map { Tuple.of(it.title, it.content) }

        return client.preparedQuery("INSERT INTO posts (title, content) VALUES ($1, $2)")
            .executeBatch(tuples)
            .map { it.rowCount() }
    }

    fun update(data: Post): Future<Int> {
        return client.preparedQuery("UPDATE posts SET title=$1, content=$2 WHERE id=$3")
            .execute(Tuple.of(data.title, data.content, data.id))
            .map { it.rowCount() }
    }

    fun deleteAll(): Future<Int> {
        return client.query("DELETE FROM posts").execute()
            .map { it.rowCount() }
    }

    fun deleteById(id: UUID): Future<Int> {
        Objects.requireNonNull(id, "id can not be null")
        return client.preparedQuery("DELETE FROM posts WHERE id=$1").execute(Tuple.of(id))
            .map { it.rowCount() }
    }

    companion object {
        private val LOGGER = Logger.getLogger(PostRepository::class.java.name)
        private val MAPPER =
            Function<Row, Post> { row: Row ->
                Post(
                    row.getUUID("id"),
                    row.getString("title"),
                    row.getString("content"),
                    row.getLocalDateTime("created_at")
                )
            }

        //factory method
        fun create(client: PgPool): PostRepository {
            return PostRepository(client)
        }
    }
}
