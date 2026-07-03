package com.example.demo;

import com.example.demo.gql.types.PostStatus;
import com.example.demo.repository.AuthorRepository;
import com.example.demo.repository.CommentRepository;
import com.example.demo.repository.PostRepository;
import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
public class DataInitializer {
    final PostRepository posts;
    final CommentRepository comments;
    final AuthorRepository authors;

    public Future<Void> run() {
        log.info("Data initialization is starting...");

        return cleanData()
                .flatMap(it -> insertData())
                .flatMap(it -> printData())
                .onSuccess(v -> log.info("Data initialization is done."))
                .mapEmpty();
    }

    Future<?> printData() {
        return Future.join(
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

                            return Future.join(insertPosts);
                        }
                );
    }

    Future<?> cleanData() {
        return Future.join(
                this.comments.deleteAll().onSuccess(event -> log.info("deleted comments: {}", event)),
                this.posts.deleteAll().onSuccess(event -> log.info("deleted posts: {}", event)),
                this.authors.deleteAll().onSuccess(event -> log.info("deleted users: {}", event))
        );
    }
}
