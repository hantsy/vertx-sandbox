package com.example.demo;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.sqlclient.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class PostRepository {
    private static final Logger log = LoggerFactory.getLogger(PostRepository.class);

    private static final Function<Row, Post> MAPPER = (Row row) ->
        new Post(
            row.getUUID("id"),
            row.getString("title"),
            row.getString("content"),
            row.getLocalDateTime("created_at")
        );


    private final Pool client;

    private PostRepository(Pool pgClient) {
        this.client = pgClient;
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
                    .toList()
            );
    }


    public Single<Post> findById(UUID id) {
        Objects.requireNonNull(id, "id can not be null");
        return client.preparedQuery("SELECT * FROM posts WHERE id=$1")
            .rxExecute(Tuple.of(id))
            .map(RowSet::iterator)
            .flatMap(iterator -> iterator.hasNext() ?
                Single.just(MAPPER.apply(iterator.next())) :
                Single.error(new PostNotFoundException(id))
            );
    }

    public Single<UUID> save(Post data) {
        String sql = "INSERT INTO posts(title, content) VALUES ($1, $2) RETURNING (id)";
        return client.preparedQuery(sql)
            .rxExecute(Tuple.of(data.title(), data.content()))
            .map(rs -> rs.iterator().next().getUUID("id"));
    }

    public Single<Integer> saveAll(List<Post> data) {
        var tuples = data.stream()
            .map(d -> Tuple.of(d.title(), d.content()))
            .collect(Collectors.toList());

        String sql = "INSERT INTO posts (title, content) VALUES ($1, $2)";
        return client.preparedQuery(sql)
            .rxExecuteBatch(tuples)
            .map(SqlResult::rowCount);
    }

    public Single<Integer> update(Post data) {
        String sql = "UPDATE posts SET title=$1, content=$2 WHERE id=$3";
        return client.preparedQuery(sql)
            .rxExecute(Tuple.of(data.title(), data.content(), data.id()))
            .map(SqlResult::rowCount);
    }

    public Single<Integer> deleteAll() {
        String sql = "DELETE FROM posts";
        return client.query(sql).rxExecute()
            .map(SqlResult::rowCount);
    }

    public Single<Integer> deleteById(UUID id) {
        Objects.requireNonNull(id, "id can not be null");
        String sql = "DELETE FROM posts WHERE id=$1";
        return client.preparedQuery(sql).rxExecute(Tuple.of(id))
            .map(SqlResult::rowCount);
    }

}
