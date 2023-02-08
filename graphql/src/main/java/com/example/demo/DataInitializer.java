package com.example.demo;


import com.example.demo.gql.types.PostStatus;
import com.example.demo.repository.AuthorRepository;
import com.example.demo.repository.CommentRepository;
import com.example.demo.repository.PostRepository;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Slf4j
@RequiredArgsConstructor
public class DataInitializer {

    final PostRepository posts;
    final CommentRepository comments;
    final AuthorRepository authors;

    @SneakyThrows
    public void run() {
        log.info("Data initialization is starting...");

        CountDownLatch latch = new CountDownLatch(1);

        cleanData()
                .flatMap(it -> insertData())
                .flatMap(it -> printData())
                .onComplete(event -> {
                    latch.countDown();
                    log.info("Data initialization is done.");
                });
        var await = latch.await(5000, TimeUnit.MILLISECONDS);
        log.debug("awaited result: {}", await);
    }

    Future<?> printData() {
        return CompositeFuture.join(
                this.posts.findAll().onSuccess(p -> log.info("saved posts: {}", p)),
                this.comments.findAll().onSuccess(p -> log.info("saved comments: {}", p)),
                this.authors.findAll().onSuccess(p -> log.info("saved authors: {}", p))
        );
    }

    Future<?> insertData() {
        return this.authors.create("user", "user@example.com")
                .flatMap(
                        authorId -> {
                            log.info("inserted user: {}", authorId);
                            var insertPosts = Stream.of("Hello vertx", "Hello vertx again")
                                    .map(title -> this.posts.create(title, "test content of " + title, PostStatus.DRAFT.name(), authorId)
                                            .onSuccess(id -> log.debug("inserted post: {}", id))
                                    )
                                    .toList();

                            var list = Arrays.stream(insertPosts.toArray(new Future[0])).toList();
                            return CompositeFuture.join(list);
                        }
                );
    }

    Future<?> cleanData() {
        return CompositeFuture.join(
                this.comments.deleteAll().onSuccess(event -> log.info("deleted comments: {}", event)),
                this.posts.deleteAll().onSuccess(event -> log.info("deleted posts: {}", event)),
                this.authors.deleteAll().onSuccess(event -> log.info("deleted users: {}", event))
        );
    }
}