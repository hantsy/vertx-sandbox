package com.example.demo;

import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.StreamSupport;

@Slf4j
@RequiredArgsConstructor
public class DataInitializer {
    private final Pool client;

    public static DataInitializer create(Pool client) {
        return new DataInitializer(client);
    }

    public Future<Void> run() {
        log.info("Data initialization is starting...");

        var authorId = java.util.UUID.randomUUID();
        Tuple user = Tuple.of(authorId, "John Doe", "john@example.com");
        Tuple first = Tuple.of("Hello Vertx", "My first post of Vertx", authorId);
        Tuple second = Tuple.of("Hello Again, Vertx", "My second post of Vertx", authorId);

        return client
            .withTransaction(
                conn -> conn.query("DELETE FROM posts").execute()
                    .flatMap(r -> conn.query("DELETE FROM users").execute())
                    .flatMap(r -> conn.preparedQuery("INSERT INTO users (id, name, email) VALUES ($1, $2, $3)")
                        .execute(user)
                    )
                    .flatMap(r -> conn.preparedQuery("INSERT INTO posts (title, content, author_id) VALUES ($1, $2, $3)")
                        .executeBatch(List.of(first, second))
                    )
                    .flatMap(r -> conn.query("SELECT * FROM posts").execute())
            )
            .onSuccess(data -> StreamSupport.stream(data.spliterator(), true)
                .forEach(row -> log.info("saved data:{}", new Object[]{row.toJson()}))
            )
            .onSuccess(v -> log.info("Data initialization is complete..."))
            .onFailure(throwable -> log.info("Data initialization failed: {}", throwable.getMessage()))
            .mapEmpty();
    }
}
