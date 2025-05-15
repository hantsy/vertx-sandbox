package com.example.demo;


import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.StreamSupport;

@Slf4j
@RequiredArgsConstructor
public class DataInitializer {
    private final Pool client;


    public static DataInitializer create(Pool client) {
        return new DataInitializer(client);
    }

    @SneakyThrows
    public void run() {
        log.info("Data initialization is starting...");

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
                .forEach(row -> log.info("saved data:{}", new Object[]{row.toJson()}))
            )
            .onComplete(
                r -> {
                    log.info("Data initialization is complete...");
                    latch.countDown();
                }
            )
            .onFailure(
                throwable -> log.debug("Data initialization is failed:{}", throwable.getMessage())
            );

        latch.await(100, TimeUnit.MILLISECONDS);
        log.debug("Data initialization is done...");
    }
}
