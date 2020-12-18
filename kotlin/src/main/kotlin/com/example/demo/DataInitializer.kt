package com.example.demo

import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.SqlConnection
import io.vertx.sqlclient.Tuple
import java.util.logging.Level
import java.util.logging.Logger
import java.util.stream.StreamSupport

class DataInitializer(private val client: PgPool) {

    fun run() {
        LOGGER.info("Data initialization is starting...")
        val first = Tuple.of("Hello Quarkus", "My first post of Quarkus")
        val second = Tuple.of("Hello Again, Quarkus", "My second post of Quarkus")
        client
            .withTransaction { conn: SqlConnection ->
                conn.query("DELETE FROM posts").execute()
                    .flatMap {
                        conn.preparedQuery("INSERT INTO posts (title, content) VALUES ($1, $2)")
                            .executeBatch(listOf(first, second))
                    }
                    .flatMap {
                        conn.query(
                            "SELECT * FROM posts"
                        ).execute()
                    }
            }
            .onSuccess { data: RowSet<Row?> ->
                StreamSupport.stream(data.spliterator(), true)
                    .forEach {
                        LOGGER.log(Level.INFO, "saved data:{0}", it!!.toJson())
                    }
            }
            .onComplete {
                //client.close(); will block the application.
                LOGGER.info("Data initialization is done...")
            }
            .onFailure { LOGGER.warning("Data initialization is failed:" + it.message) }
    }

    companion object {
        private val LOGGER = Logger.getLogger(DataInitializer::class.java.name)
        fun create(client: PgPool): DataInitializer {
            return DataInitializer(client)
        }
    }
}
