package com.example.demo

import io.vertx.kotlin.coroutines.coAwait
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.SqlConnection
import io.vertx.sqlclient.Tuple
import java.util.logging.Logger

class DataInitializer(private val client: Pool) {

    suspend fun run() {
        LOGGER.info("Data initialization is starting...")
        val first = Tuple.of("Hello Vertx", "My first post of Vertx")
        val second = Tuple.of("Hello Again, Vertx", "My second post of Vertx")


        val result = client
            .withTransaction { conn: SqlConnection ->
                conn.query("DELETE FROM posts")
                    .execute()
                    .flatMap {
                        conn.preparedQuery("INSERT INTO posts (title, content) VALUES ($1, $2)")
                            .executeBatch(listOf(first, second))
                    }
                    .flatMap {
                        conn.query("SELECT * FROM posts")
                            .execute()
                    }

            }
            .coAwait()

        result.forEach { println(it.toJson()) }
        LOGGER.info("Data initialization is done...")
    }

    companion object {
        private val LOGGER = Logger.getLogger(DataInitializer::class.java.name)
    }
}
