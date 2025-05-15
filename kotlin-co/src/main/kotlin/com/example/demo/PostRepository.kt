package com.example.demo

import io.vertx.kotlin.coroutines.coAwait
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.Tuple
import java.util.*
import java.util.logging.Logger
import java.util.stream.StreamSupport

class PostRepository(private val client: Pool) {

    suspend fun findAll(): List<Post> {
        val sql = "SELECT * FROM posts ORDER BY id ASC"
        return client.query(sql)
            .execute()
            .map { rs: RowSet<Row?> ->
                StreamSupport.stream(rs.spliterator(), false)
                    .map { MAPPER(it!!) }
                    .toList()
            }
            .coAwait()
    }


    suspend fun findById(id: UUID): Post {
        val sql = "SELECT * FROM posts WHERE id=$1"
        return client.preparedQuery(sql)
            .execute(Tuple.of(id))
            .map { it.iterator() }
            .map { if (it.hasNext()) MAPPER(it.next()) else throw PostNotFoundException(id) }
            .coAwait()
    }


    suspend fun save(data: Post): UUID? {
        val sql = "INSERT INTO posts(title, content) VALUES ($1, $2) RETURNING (id)"
        return client.preparedQuery(sql)
            .execute(Tuple.of(data.title, data.content))
            .map { it.iterator().next().getUUID("id") }
            .coAwait()
    }


    suspend fun saveAll(data: List<Post>): Int? {
        val tuples = data.map { Tuple.of(it.title, it.content) }
        val sql = "INSERT INTO posts (title, content) VALUES ($1, $2)"
        return client.preparedQuery(sql)
            .executeBatch(tuples)
            .map { it.rowCount() }
            .coAwait()
    }

    suspend fun update(data: Post): Int? {
        val sql = "UPDATE posts SET title=$1, content=$2 WHERE id=$3"
        return client.preparedQuery(sql)
            .execute(Tuple.of(data.title, data.content, data.id))
            .map { it.rowCount() }
            .coAwait()
    }


    suspend fun deleteAll(): Int? {
        val sql = "DELETE FROM posts"
        return client.query(sql).execute()
            .map { it.rowCount() }
            .coAwait()
    }


    suspend fun deleteById(id: UUID): Int {
        val sql = "DELETE FROM posts WHERE id=$1"
        return client.preparedQuery(sql).execute(Tuple.of(id))
            .map { it.rowCount() }
            .coAwait()
    }

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
