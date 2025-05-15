package com.example.demo.gql;

import com.example.demo.gql.types.*;
import com.example.demo.service.PostService;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.observables.ConnectableObservable;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.ext.web.handler.graphql.ws.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dataloader.DataLoader;
import org.reactivestreams.Publisher;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

@RequiredArgsConstructor
@Slf4j
public class DataFetchers {
    private final PostService posts;

    public DataFetcher<CompletionStage<List<Post>>> getAllPosts() {
        return (DataFetchingEnvironment dfe) -> this.posts.getAllPosts().toCompletionStage();
    }

    public DataFetcher<CompletionStage<Post>> getPostById() {
        return (DataFetchingEnvironment dfe) -> {
            String postId = dfe.getArgument("postId");
            return posts.getPostById(postId).toCompletionStage();
        };
    }


    public DataFetcher<CompletionStage<UUID>> createPost() {
        return (DataFetchingEnvironment dfe) -> {
            var postInputArg = dfe.getArgument("createPostInput");
            var jacksonMapper = DatabindCodec.mapper();
            var input = jacksonMapper.convertValue(postInputArg, CreatePostInput.class);
            return this.posts.createPost(input).toCompletionStage();
        };
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

    public DataFetcher<CompletionStage<UUID>> addComment() {
        return (DataFetchingEnvironment dfe) -> {
            var commentInputArg = dfe.getArgument("commentInput");
            var jacksonMapper = DatabindCodec.mapper();
            var input = jacksonMapper.convertValue(commentInputArg, CommentInput.class);
            return this.posts.addComment(input)
                    .onSuccess(id -> this.posts.getCommentById(id.toString()).onSuccess(subject::onNext))
                    .toCompletionStage();
        };
    }

    private final ReplaySubject<Comment> subject = ReplaySubject.create(1);

    public DataFetcher<Publisher<Comment>> commentAdded() {
        return (DataFetchingEnvironment dfe) -> {
            Message message = dfe.getLocalContext();
            log.info("msg: {}, connectionParams: {}", message.message(), message.connectionParams());
            ConnectableObservable<Comment> connectableObservable = subject.share().publish();
            connectableObservable.connect();
            log.info("connect to `commentAdded`");
            return connectableObservable.toFlowable(BackpressureStrategy.BUFFER);
        };
    }

//    public DataFetcher<Boolean> upload() {
//        return (DataFetchingEnvironment dfe) -> {
//
//            FileUpload upload = dfe.getArgument("file");
//            log.info("name: {}", upload.name());
//            log.info("file name: {}", upload.fileName());
//            log.info("uploaded file name: {}", upload.uploadedFileName());
//            log.info("content type: {}", upload.contentType());
//            log.info("charset: {}", upload.charSet());
//            log.info("size: {}", upload.size());
////            String fileContent = Vertx.vertx().fileSystem().readFileBlocking(upload.uploadedFileName()).toString();
////            log.info("file content: {}", fileContent);
//            return true;
//        };
//    }
}
