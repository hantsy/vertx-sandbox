package com.example.demo.gql;

import com.example.demo.gql.types.Author;
import com.example.demo.gql.types.Comment;
import com.example.demo.service.AuthorService;
import com.example.demo.service.PostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dataloader.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

@Slf4j
@RequiredArgsConstructor
public class DataLoaders {
    final AuthorService authorService;
    final PostService postService;

    public DataLoader<String, Author> authorsLoader() {
        var batchLoader = new BatchLoaderWithContext<String, Author>() {
            @Override
            public CompletionStage<List<Author>> load(List<String> keys, BatchLoaderEnvironment batchLoaderEnvironment) {
                return authorService.getAuthorByIdIn(keys).toCompletionStage();
            }
        };

        return DataLoaderFactory.newDataLoader(batchLoader);
    }

    public DataLoader<String, List<Comment>> commentsLoader() {
        var batchLoader = new MappedBatchLoaderWithContext<String, List<Comment>>() {
            @Override
            public CompletionStage<Map<String, List<Comment>>> load(
                Set<String> keys,
                BatchLoaderEnvironment batchLoaderEnvironment
            ) {
                return postService.getCommentsByPostIdIn(keys)
                    .map(
                        comments -> {
                            log.info("comments of post: {}", comments);
                            Map<String, List<Comment>> mappedComments = new HashMap<>();
                            keys.forEach(
                                k -> mappedComments.put(k, comments
                                    .stream()
                                    .filter(c -> c.getPostId().equals(k)).toList())
                            );
                            log.info("mapped comments: {}", mappedComments);
                            return mappedComments;
                        }
                    )
                    .toCompletionStage();
            }
        };
        return DataLoaderFactory.newMappedDataLoader(batchLoader);
    }
}
