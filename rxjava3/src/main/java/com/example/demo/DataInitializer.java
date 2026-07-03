package com.example.demo;

import io.reactivex.rxjava3.core.Completable;
import io.vertx.rxjava3.sqlclient.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DataInitializer {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final Pool client;

    public DataInitializer(Pool client) {
        this.client = client;
    }

    public static DataInitializer create(Pool client) {
        return new DataInitializer(client);
    }

    public Completable run() {
        log.info("Data initialization is starting...");

        Tuple first = Tuple.of("Hello Vertx", "My first post of Vertx");
        Tuple second = Tuple.of("Hello Again, Vertx", "My second post of Vertx");

        return client
            .rxWithTransaction(
                (SqlConnection tx) -> tx.query("DELETE FROM posts").rxExecute()
                    .flatMap(result -> tx.preparedQuery("INSERT INTO posts (title, content) VALUES ($1, $2)").rxExecuteBatch(List.of(first, second)))
                    .toMaybe()
            )
            .flatMapSingle(d -> client.query("SELECT * FROM posts").rxExecute())
            .doOnSuccess(data -> data.forEach(row -> log.info("saved row: {}", row.toJson())))
            .doOnError(err -> log.warn("failed to initializing: {}", err.getMessage()))
            .ignoreElement();
    }
}
