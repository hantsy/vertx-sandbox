package com.example.demo

import io.vertx.kotlin.coroutines.await
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.Tuple
import java.util.*
import java.util.logging.Logger
import java.util.stream.StreamSupport

class PostRepository(private val client: Pool) {

    suspend fun findAll() = client.query("SELECT * FROM posts ORDER BY id ASC")
        .execute()
        .map { rs: RowSet<Row?> ->
            StreamSupport.stream(rs.spliterator(), false)
                .map { MAPPER(it!!) }
                .toList()
        }
        .await()


    suspend fun findById(id: UUID): Post? = client.preparedQuery("SELECT * FROM posts WHERE id=$1")
        .execute(Tuple.of(id))
        .map { it.iterator() }
        .map { if (it.hasNext()) MAPPER(it.next()) else throw PostNotFoundException(id) }
        .await()


    suspend fun save(data: Post) =
        client.preparedQuery("INSERT INTO posts(title, content) VALUES ($1, $2) RETURNING (id)")
            .execute(Tuple.of(data.title, data.content))
            .map { it.iterator().next().getUUID("id") }
            .await()


    suspend fun saveAll(data: List<Post>): Int? {
        val tuples = data.map { Tuple.of(it.title, it.content) }

        return client.preparedQuery("INSERT INTO posts (title, content) VALUES ($1, $2)")
            .executeBatch(tuples)
            .map { it.rowCount() }
            .await()
    }

    suspend fun update(data: Post) = client.preparedQuery("UPDATE posts SET title=$1, content=$2 WHERE id=$3")
        .execute(Tuple.of(data.title, data.content, data.id))
        .map { it.rowCount() }
        .await()


    suspend fun deleteAll() = client.query("DELETE FROM posts").execute()
        .map { it.rowCount() }
        .await()


    suspend fun deleteById(id: UUID) = client.preparedQuery("DELETE FROM posts WHERE id=$1").execute(Tuple.of(id))
        .map { it.rowCount() }
        .await()

    companion object {
        private val LOGGER = Logger.getLogger(PostRepository::class.java.name)
        val MAPPER: (Row) -> Post = { row: Row ->
            Post(
                row.getUUID("id"),
                row.getString("title"),
                row.getString("content"),
                row.getLocalDateTime("created_at")
            )
        }

    }
}
