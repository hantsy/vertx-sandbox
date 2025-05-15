package com.example.demo;

import io.vertx.rxjava3.sqlclient.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class DataInitializer {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final Pool client;

    public DataInitializer(Pool client) {
        this.client = client;
    }

    public static DataInitializer create(Pool client) {
        return new DataInitializer(client);
    }

    public void run() {
        log.info("Data initialization is starting...");

        Tuple first = Tuple.of("Hello Vertx", "My first post of Vertx");
        Tuple second = Tuple.of("Hello Again, Vertx", "My second post of Vertx");

        var latch = new CountDownLatch(1);

        client
            .rxWithTransaction(
                (SqlConnection tx) -> tx.query("DELETE FROM posts").rxExecute()
                    .flatMap(result -> tx.preparedQuery("INSERT INTO posts (title, content) VALUES ($1, $2)").rxExecuteBatch(List.of(first, second)))
                    .toMaybe()
            )
            .flatMapSingle(d -> client.query("SELECT * FROM posts").rxExecute())
            .doOnTerminate(latch::countDown)
            .subscribe(
                (RowSet<Row> data) -> {
                    data.forEach(row -> log.info("saved row: {}", row.toJson()));
                    log.debug("Data initialization is completed successfully...");
                },
                err -> {
                    log.warn("failed to initializing: {}", err.getMessage());
                }
            );

        try {
            latch.await(1000, TimeUnit.MILLISECONDS);
            log.info("Data initialization is done...");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
