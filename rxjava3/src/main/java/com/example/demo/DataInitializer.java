package com.example.demo;

import io.vertx.rxjava3.pgclient.PgPool;
import io.vertx.rxjava3.sqlclient.Pool;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import io.vertx.rxjava3.sqlclient.Tuple;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
public class DataInitializer {

    private Pool client;

    public DataInitializer(Pool client) {
        this.client = client;
    }

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
            .rxWithTransaction(
                (SqlConnection tx) -> tx.query("DELETE FROM posts").rxExecute()
                    .flatMap(result -> tx.preparedQuery("INSERT INTO posts (title, content) VALUES ($1, $2)").rxExecuteBatch(List.of(first, second)))
                    .toMaybe()
            )
            .flatMapSingle(d -> client.query("SELECT * FROM posts").rxExecute())
            .doOnComplete(latch::countDown)
            .subscribe(
                (data) -> {
                    data.forEach(row -> {
                        log.info("saved row: {}", row.toJson());
                    });
                },
                err -> log.warn("failed to initializing: {}", err.getMessage())
            );

        latch.await(500, TimeUnit.MILLISECONDS);
        log.info("Data initialization is done...");
    }
}
