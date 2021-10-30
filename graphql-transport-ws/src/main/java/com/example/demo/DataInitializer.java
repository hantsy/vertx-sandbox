package com.example.demo;


import com.example.demo.gql.types.PostStatus;
import com.example.demo.repository.AuthorRepository;
import com.example.demo.repository.CommentRepository;
import com.example.demo.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Random;
import java.util.stream.IntStream;

@Slf4j
@RequiredArgsConstructor
public class DataInitializer {

    final PostRepository posts;
    final CommentRepository comments;
    final AuthorRepository authors;


    public void run() {
        log.info("Data initialization is starting...");

        this.comments.deleteAll().onSuccess(event -> log.info("deleted comments: {}", event))
                .flatMap(r -> this.posts.deleteAll().onSuccess(event -> log.info("deleted posts: {}", event)))
                .flatMap(r -> this.authors.deleteAll().onSuccess(event -> log.info("deleted users: {}", event)))
                .flatMap(r -> this.authors.create("user", "user@example.com")
                        .onSuccess(
                                authorId -> {
                                    log.info("inserted user: {}", authorId);
                                    IntStream.range(1, 5)
                                            .forEach(
                                                    i -> this.posts.create("Dgs post #" + i, "test content of #" + i, PostStatus.DRAFT.name(), authorId)
                                                            .onSuccess(
                                                                    postId -> {
                                                                        log.info("inserted post: {}", postId);
                                                                        IntStream.range(1, new Random().nextInt(5) + 1)
                                                                                .forEach(c -> this.comments.create("comment #" + c, postId)
                                                                                        .onSuccess(id -> log.info("inserted comment: {}", id))
                                                                                );
                                                                    }
                                                            )
                                                            .onFailure(event -> log.debug("failed:" + event))
                                            );
                                }
                        )
                )
                .flatMap(r -> this.posts.findAll().onSuccess(p -> log.info("saved posts: {}", p)))
                .flatMap(r -> this.comments.findAll().onSuccess(p -> log.info("saved comments: {}", p)))
                .flatMap(r -> this.authors.findAll().onSuccess(p -> log.info("saved authors: {}", p)))
                .onComplete(event -> log.info("Data initialization is done."));
    }
}
