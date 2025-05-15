package com.example.demo;

import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.StreamSupport;

@ApplicationScoped
public class DataInitializer {

    private final static Logger LOGGER = Logger.getLogger(DataInitializer.class.getName());

    private  Pool client;

    public DataInitializer() {
    }

    @Inject
    public DataInitializer(Pool client) {
        this.client = client;
    }

    public void run(@Observes @Initialized(ApplicationScoped.class) Object o) throws InterruptedException {
        LOGGER.info("Data initialization is starting...");

        Tuple first = Tuple.of("Hello Quarkus", "My first post of Quarkus");
        Tuple second = Tuple.of("Hello Again, Quarkus", "My second post of Quarkus");

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
                    LOGGER.info("Data initialization is completed...");
                    latch.countDown();
                }
            )
            .onFailure(
                throwable -> LOGGER.warning("Data initialization is failed:" + throwable.getMessage())
            );

        latch.await(1000, TimeUnit.MILLISECONDS);
        LOGGER.log(Level.INFO, "Data initialization is done...");
    }
}
