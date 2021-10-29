package com.example.demo;

import io.vertx.rxjava3.pgclient.PgPool;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import io.vertx.rxjava3.sqlclient.Tuple;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class DataInitializer {

    private PgPool client;

    public DataInitializer(PgPool client) {
        this.client = client;
    }

    public static DataInitializer create(PgPool client) {
        return new DataInitializer(client);
    }

    public void run() {
        log.info("Data initialization is starting...");

        Tuple first = Tuple.of("Hello Vertx", "My first post of Vertx");
        Tuple second = Tuple.of("Hello Again, Vertx", "My second post of Vertx");

        client
            .rxWithTransaction(
                (SqlConnection tx) -> tx.query("DELETE FROM posts").rxExecute()
                    .flatMap(result -> tx.preparedQuery("INSERT INTO posts (title, content) VALUES ($1, $2)").rxExecuteBatch(List.of(first, second)))
                    .toMaybe()
            )
            .flatMapSingle(d -> client.query("SELECT * FROM posts").rxExecute())
            .subscribe(
                (data) -> {
                    data.forEach(row -> {
                        log.info("saved row: {}", row.toJson());
                    });
                },
                err -> log.warn("failed to initializing: {}", err.getMessage())
            );
    }
}
