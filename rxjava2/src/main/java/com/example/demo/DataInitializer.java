package com.example.demo;

import io.vertx.reactivex.pgclient.PgPool;
import io.vertx.reactivex.sqlclient.SqlConnection;
import io.vertx.reactivex.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


public class DataInitializer {

    private final static Logger LOGGER = LoggerFactory.getLogger(DataInitializer.class.getName());

    private PgPool client;

    public DataInitializer(PgPool client) {
        this.client = client;
    }

    public static DataInitializer create(PgPool client) {
        return new DataInitializer(client);
    }

    public void run() {
        LOGGER.info("Data initialization is starting...");

        Tuple first = Tuple.of("Hello Quarkus", "My first post of Quarkus");
        Tuple second = Tuple.of("Hello Again, Quarkus", "My second post of Quarkus");

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
                        LOGGER.info("saved row: {}", row.toJson());
                    });
                },
                err -> LOGGER.warn("failed to initializing: {}", err.getMessage())
            );
    }
}
