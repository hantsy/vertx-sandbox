package com.example.demo;


import com.example.demo.gql.types.PostStatus;
import com.example.demo.repository.AuthorRepository;
import com.example.demo.repository.CommentRepository;
import com.example.demo.repository.PostRepository;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

@Slf4j
@RequiredArgsConstructor
public class DataInitializer {

    final PostRepository posts;
    final CommentRepository comments;
    final AuthorRepository authors;


    public void run() {
        log.info("Data initialization is starting...");
        var deleteComments = this.comments.deleteAll().onSuccess(event -> log.info("deleted comments: {}", event));
        var deletePosts = this.posts.deleteAll().onSuccess(event -> log.info("deleted posts: {}", event));
        var deleteUsers = this.authors.deleteAll().onSuccess(event -> log.info("deleted users: {}", event));

        //log.info("deleted rows: authors: {}, comments: {}, posts: {}", authorsDel, commentsDel, postDel);
        var insertData = this.authors.create("user", "user@example.com").onSuccess(
            authorId -> {
                IntStream.range(1, 5)
                    .forEach(
                        i -> {
                            this.posts.create("Dgs post #" + i, "test content of #" + i, PostStatus.DRAFT.name(), authorId).onSuccess(
                                postId -> {

                                    IntStream.range(1, new Random().nextInt(5) + 1)
                                        .forEach(c -> this.comments.create("comment #" + c, postId));
                                }
                            );

                        }
                    );
            }
        );

        var printPosts = this.posts.findAll().onSuccess(p -> log.info("post: {}", p));
        var printComments = this.comments.findAll().onSuccess(p -> log.info("comment: {}", p));
        var printAuthors = this.authors.findAll().onSuccess(p -> log.info("author: {}", p));

        deleteComments
            .flatMap(integer -> deletePosts)
            .flatMap(integer -> deleteUsers)
            .flatMap(integer -> insertData)
            .flatMap( uuid -> printPosts)
            .flatMap(postEntities -> printComments)
            .flatMap(commentEntities -> printAuthors)
            .onSuccess(event -> log.info("done"));
        log.info("done data initialization...");
    }
}
