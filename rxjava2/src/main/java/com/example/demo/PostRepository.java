package com.example.demo;


import io.reactivex.Flowable;
import io.reactivex.Single;
import io.vertx.reactivex.pgclient.PgPool;
import io.vertx.reactivex.sqlclient.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class PostRepository {
    private static final Logger LOGGER = Logger.getLogger(PostRepository.class.getName());

    private static Function<Row, Post> MAPPER = (row) ->
        Post.of(
            row.getUUID("id"),
            row.getString("title"),
            row.getString("content"),
            row.getLocalDateTime("created_at")
        );


    private final Pool client;

    private PostRepository(Pool _client) {
        this.client = _client;
    }

    //factory method
    public static PostRepository create(Pool client) {
        return new PostRepository(client);
    }

    public Flowable<Post> findAll() {
        return this.client
            .query("SELECT * FROM posts")
            .rxExecute()
            .flattenAsFlowable(
                rows -> StreamSupport.stream(rows.spliterator(), false)
                    .map(MAPPER)
                    .collect(Collectors.toList())
            );
    }


    public Single<Post> findById(UUID id) {
        Objects.requireNonNull(id, "id can not be null");
        return client.preparedQuery("SELECT * FROM posts WHERE id=$1").rxExecute(Tuple.of(id))
            .map(RowSet::iterator)
            .flatMap(iterator -> iterator.hasNext() ? Single.just(MAPPER.apply(iterator.next())) : Single.error(new PostNotFoundException(id)));
    }

    public Single<UUID> save(Post data) {
        return client.preparedQuery("INSERT INTO posts(title, content) VALUES ($1, $2) RETURNING (id)")
            .rxExecute(Tuple.of(data.getTitle(), data.getContent()))
            .map(rs -> rs.iterator().next().getUUID("id"));
    }

    public Single<Integer> saveAll(List<Post> data) {
        var tuples = data.stream()
            .map(d -> Tuple.of(d.getTitle(), d.getContent()))
            .collect(Collectors.toList());

        return client.preparedQuery("INSERT INTO posts (title, content) VALUES ($1, $2)")
            .rxExecuteBatch(tuples)
            .map(SqlResult::rowCount);
    }

    public Single<Integer> update(Post data) {
        return client.preparedQuery("UPDATE posts SET title=$1, content=$2 WHERE id=$3")
            .rxExecute(Tuple.of(data.getTitle(), data.getContent(), data.getId()))
            .map(SqlResult::rowCount);
    }

    public Single<Integer> deleteAll() {
        return client.query("DELETE FROM posts").rxExecute()
            .map(SqlResult::rowCount);
    }

    public Single<Integer> deleteById(UUID id) {
        Objects.requireNonNull(id, "id can not be null");
        return client.preparedQuery("DELETE FROM posts WHERE id=$1").rxExecute(Tuple.of(id))
            .map(SqlResult::rowCount);
    }

}
