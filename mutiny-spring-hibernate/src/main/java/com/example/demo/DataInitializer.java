package com.example.demo;


import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Tuple;
import lombok.RequiredArgsConstructor;
import org.hibernate.reactive.mutiny.Mutiny;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.StreamSupport;

@Component
@RequiredArgsConstructor
public class DataInitializer {

    private final static Logger LOGGER = Logger.getLogger(DataInitializer.class.getName());

    private final Mutiny.SessionFactory sessionFactory;

    @EventListener(ContextRefreshedEvent.class)
    public void run() {
        LOGGER.info("Data initialization is starting...");

        Post first = Post.of(null, "Hello Quarkus", "My first post of Quarkus", null);
        Post second = Post.of(null, "Hello Again, Quarkus", "My second post of Quarkus", null);

        sessionFactory
            .withTransaction(
                (conn, tx) -> conn.createQuery("DELETE FROM Post").executeUpdate()
                    .flatMap(r -> conn.persistAll(first, second))
                    .flatMap(r -> conn.createQuery("SELECT p from Post p", Post.class).getResultList())
            )
            .subscribe()
            .with(
                data -> LOGGER.log(Level.INFO, "saved data:{0}", data),
                throwable -> LOGGER.warning("Data initialization is failed:" + throwable.getMessage())
            );
    }
}
