package com.example.demo

import io.vertx.core.Future
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.Tuple
import java.util.*
import java.util.logging.Logger
import java.util.stream.StreamSupport

class PostRepository(private val client: Pool) {

    fun findAll() = client.query("SELECT * FROM posts ORDER BY id ASC")
        .execute()
        .map { rs: RowSet<Row?> ->
            StreamSupport.stream(rs.spliterator(), false)
                .map { mapFun(it!!) }
                .toList()
        }


    fun findById(id: UUID) = client.preparedQuery("SELECT * FROM posts WHERE id=$1")
        .execute(Tuple.of(id))
        .map { it.iterator() }
        .map {
            if (it.hasNext()) mapFun(it.next());
            else throw PostNotFoundException(id)
        }


    fun save(data: Post) = client.preparedQuery("INSERT INTO posts(title, content) VALUES ($1, $2) RETURNING (id)")
        .execute(Tuple.of(data.title, data.content))
        .map { it.iterator().next().getUUID("id") }


    fun saveAll(data: List<Post>): Future<Int> {
        val tuples = data.map { Tuple.of(it.title, it.content) }

        return client.preparedQuery("INSERT INTO posts (title, content) VALUES ($1, $2)")
            .executeBatch(tuples)
            .map { it.rowCount() }
    }

    fun update(data: Post) = client.preparedQuery("UPDATE posts SET title=$1, content=$2 WHERE id=$3")
        .execute(Tuple.of(data.title, data.content, data.id))
        .map { it.rowCount() }


    fun deleteAll() = client.query("DELETE FROM posts").execute()
        .map { it.rowCount() }


    fun deleteById(id: UUID) = client.preparedQuery("DELETE FROM posts WHERE id=$1").execute(Tuple.of(id))
        .map { it.rowCount() }

    companion object {
        private val LOGGER = Logger.getLogger(PostRepository::class.java.name)
        val mapFun: (Row) -> Post = { row: Row ->
            Post(
                row.getUUID("id"),
                row.getString("title"),
                row.getString("content"),
                row.getLocalDateTime("created_at")
            )
        }

    }
}
