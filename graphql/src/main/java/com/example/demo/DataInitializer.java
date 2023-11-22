package com.example.demo;


import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Tuple;
import lombok.SneakyThrows;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.StreamSupport;

public class DataInitializer {

    private final static Logger LOGGER = Logger.getLogger(DataInitializer.class.getName());

    private Pool client;

    public DataInitializer(Pool client) {
        this.client = client;
    }

    public static DataInitializer create(Pool client) {
        return new DataInitializer(client);
    }

    @SneakyThrows
    public void run() {
        LOGGER.info("Data initialization is starting...");

        Tuple first = Tuple.of("Hello Vertx", "My first post of Vertx");
        Tuple second = Tuple.of("Hello Again, Vertx", "My second post of Vertx");

        var latch = new CountDownLatch(1);
        client
            .withTransaction(
                conn -> conn.query("DELETE FROM posts").execute()
                    .flatMap(r -> conn.preparedQuery("INSERT INTO posts (title, content) VALUES ($1, $2)")
                        .executeBatch(List.of(first, second))
                    )
                    .flatMap(r -> conn.query("SELECT * FROM posts").execute())
            )
            .onSuccess(data -> StreamSupport.stream(data.spliterator(), true)
                .forEach(row -> LOGGER.log(Level.INFO, "saved data:{0}", new Object[]{row.toJson()}))
            )
            .onComplete(
                r -> {
                    //client.close(); will block the application.
                    latch.countDown();
                    LOGGER.info("Data initialization is done...");
                }
            )
            .onFailure(
                throwable -> LOGGER.warning("Data initialization is failed:" + throwable.getMessage())
            );

        latch.await(500, TimeUnit.MILLISECONDS);
    }
}
