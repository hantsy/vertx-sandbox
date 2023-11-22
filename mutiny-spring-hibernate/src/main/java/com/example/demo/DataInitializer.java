package com.example.demo;


import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.hibernate.reactive.mutiny.Mutiny;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
@RequiredArgsConstructor
public class DataInitializer {

    private final static Logger LOGGER = Logger.getLogger(DataInitializer.class.getName());

    private final Mutiny.SessionFactory sessionFactory;

    @SneakyThrows
    @EventListener(ContextRefreshedEvent.class)
    public void run() {
        LOGGER.info("Data initialization is starting...");

        Post first = Post.of(null, "Hello Quarkus", "My first post of Quarkus", null);
        Post second = Post.of(null, "Hello Again, Quarkus", "My second post of Quarkus", null);

        var latch = new CountDownLatch(1);
        sessionFactory
            .withTransaction(
                (conn, tx) -> conn.createQuery("DELETE FROM Post").executeUpdate()
                    .flatMap(r -> conn.persistAll(first, second))
                    .chain(conn::flush)
                    .flatMap(r -> conn.createQuery("SELECT p from Post p", Post.class).getResultList())
            )
            .onTermination().invoke(latch::countDown)
            .subscribe()
            .with(
                data -> LOGGER.log(Level.INFO, "saved data:{0}", data),
                throwable -> LOGGER.warning("Data initialization is failed:" + throwable.getMessage())
            );
        latch.await(500, TimeUnit.MILLISECONDS);
    }
}
