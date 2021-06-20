package com.example.demo.gql;

import com.example.demo.gql.types.Author;
import com.example.demo.gql.types.Comment;
import com.example.demo.gql.types.CreatePostInput;
import com.example.demo.gql.types.Post;
import com.example.demo.service.PostService;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.ext.web.handler.graphql.schema.VertxDataFetcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dataloader.DataLoader;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

@RequiredArgsConstructor
@Slf4j
public class DataFetchers {
    private final PostService posts;

    public VertxDataFetcher<List<Post>> getAllPosts() {
        return VertxDataFetcher.create(
            (DataFetchingEnvironment dfe) -> {
                return this.posts.getAllPosts();
            });
    }

    public VertxDataFetcher<Post> getPostById() {
        return VertxDataFetcher.create((DataFetchingEnvironment dfe) -> {
            String postId = dfe.getArgument("postId");
            return posts.getPostById(postId);
        });
    }


    public VertxDataFetcher<UUID> createPost() {
        return VertxDataFetcher.create((DataFetchingEnvironment dfe) -> {
            var postInputArg = dfe.getArgument("createPostInput");
            var jacksonMapper = DatabindCodec.mapper();
            var input = jacksonMapper.convertValue(postInputArg, CreatePostInput.class);
            return this.posts.createPost(input);
        });
    }

    public DataFetcher<CompletionStage<List<Comment>>> commentsOfPost() {
        return (DataFetchingEnvironment dfe) -> {
            DataLoader<String, List<Comment>> dataLoader = dfe.getDataLoader("commentsLoader");
            Post post = dfe.getSource();
            //log.info("source: {}", post);
            return dataLoader.load(post.getId());
        };
    }

    public DataFetcher<CompletionStage<Author>> authorOfPost() {
        return (DataFetchingEnvironment dfe) -> {
            DataLoader<String, Author> dataLoader = dfe.getDataLoader("authorsLoader");
            Post post = dfe.getSource();
            //log.info("source: {}", post);
            return dataLoader.load(post.getAuthorId());
        };
    }
}
