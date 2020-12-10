package com.example.demo;


import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Tuple;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.StreamSupport;

public class DataInitializer {

    private final static Logger LOGGER = Logger.getLogger(DataInitializer.class.getName());

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

        client.query("DELETE FROM posts").execute()
            .compose(r -> client.preparedQuery("INSERT INTO posts (title, content) VALUES ($1, $2)")
                .executeBatch(List.of(first, second))
            )
            .compose(r -> client.query("SELECT * FROM posts").execute())
            .onSuccess(data -> StreamSupport.stream(data.spliterator(), true)
                .forEach(row -> LOGGER.log(Level.INFO, "saved data:{0}", new Object[]{row.toJson()}))
            )
            .onComplete(
                r -> {
                    //client.close();
                    LOGGER.info("Data initialization is done...");
                }
            )
            .onFailure(
                throwable -> LOGGER.warning("failed")
            );
    }
}
