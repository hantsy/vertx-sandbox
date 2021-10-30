package com.example.demo.service;

import com.example.demo.gql.types.*;
import com.example.demo.model.CommentEntity;
import com.example.demo.model.PostEntity;
import com.example.demo.repository.AuthorRepository;
import com.example.demo.repository.CommentRepository;
import com.example.demo.repository.PostRepository;
import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

@RequiredArgsConstructor
public class PostService {

    private static final Function<PostEntity, Post> POST_MAPPER = p -> Post.builder()
        .id(p.id().toString())
        .title(p.title())
        .content(p.content())
        .status(PostStatus.valueOf(p.status()))
        .createdAt(p.createdAt())
        .authorId(p.authorId().toString())
        .build();
    public static final Function<CommentEntity, Comment> COMMENT_MAPPER = c -> Comment.builder()
        .id(c.id().toString())
        .content(c.content())
        .createdAt(c.createdAt())
        .postId(c.postId().toString())
        .build();

    final PostRepository posts;
    final CommentRepository comments;
    final AuthorRepository authors;


    public Future<List<Post>> getAllPosts() {
        return this.posts.findAll()
            .map(
                posts -> posts.stream()
                    .map(POST_MAPPER)
                    .toList()
            );
    }

    public Future<Post> getPostById(String id) {
        var postEntity = this.posts.findById(UUID.fromString(id));
        return postEntity.map(POST_MAPPER);
    }

    Future<List<Post>> getPostsByAuthorId(String id) {
        return this.posts.findByAuthorId(UUID.fromString(id))
            .map(
                posts -> posts.stream()
                    .map(POST_MAPPER)
                    .toList()
            );
    }

    public Future<UUID> createPost(CreatePostInput postInput) {
        Validate.notNull(postInput, "CreatePostInput can not be null");
        Validate.notEmpty(postInput.getTitle(), "CreatePostInput.title can not be empty");
        // Use a hard code user id here.
        // In a real world application, the author is the current user which can be fetched from Spring security context.
        return this.authors.findAll().flatMap(
            result -> {
                var authorId = result.get(0).id();
                return this.posts.create(postInput.getTitle(), postInput.getContent(), "DRAFT", authorId);
            }
        );

    }

    public Future<UUID> addComment(CommentInput input) {
        return this.comments.create(input.getContent(), UUID.fromString(input.getPostId()));
    }

    public Future<Comment> getCommentById(String id) {
        var commentById = this.comments.findById(UUID.fromString(id));
        return commentById.map(COMMENT_MAPPER);
    }

    public Future<List<Comment>> getCommentsByPostId(String id) {
        return this.comments.findByPostId(UUID.fromString(id))
            .map(comments -> comments.stream()
                .map(COMMENT_MAPPER)
                .toList()
            );
    }

    public Future<List<Comment>> getCommentsByPostIdIn(Set<String> ids) {
        var uuids = ids.stream().map(UUID::fromString).toList();
        return this.comments.findByPostIdIn(uuids)
            .map(comments -> comments.stream()
                .map(COMMENT_MAPPER)
                .toList()
            );
    }
}
