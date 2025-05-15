package com.example.demo

import io.vertx.sqlclient.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger
import java.util.stream.StreamSupport

class DataInitializer(private val client: Pool) {

    fun run() {
        LOGGER.info("Data initialization is starting...")
        val first = Tuple.of("Hello Quarkus", "My first post of Quarkus")
        val second = Tuple.of("Hello Again, Quarkus", "My second post of Quarkus")

        val latch = CountDownLatch(1)
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
                LOGGER.info("Data initialization is completed...")
                latch.countDown()
            }
            .onFailure { LOGGER.warning("Data initialization is failed:" + it.message) }

        latch.await(1000, TimeUnit.MILLISECONDS)
    }

    companion object {
        private val LOGGER = Logger.getLogger(DataInitializer::class.java.name)
    }
}
