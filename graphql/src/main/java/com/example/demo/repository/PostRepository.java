package com.example.demo.repository;

import com.example.demo.model.PostEntity;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@RequiredArgsConstructor
@Slf4j
public class PostRepository {
    private static Function<Row, PostEntity> MAPPER = (row) ->
        new PostEntity(
            row.getUUID("id"),
            row.getString("title"),
            row.getString("content"),
            row.getString("status"),
            row.getLocalDateTime("created_at"),
            row.getUUID("author_id")
        );


    private final Pool client;

    public Future<List<PostEntity>> findAll() {
        return client.query("SELECT * FROM posts ORDER BY created_at DESC ")
            .execute()
            .map(rs -> StreamSupport.stream(rs.spliterator(), false)
                .map(MAPPER)
                .collect(Collectors.toList())
            );
    }


    public Future<PostEntity> findById(UUID id) {
        Objects.requireNonNull(id, "id can not be null");
        return client.preparedQuery("SELECT * FROM posts WHERE id=$1").execute(Tuple.of(id))
            .map(RowSet::iterator)
            .map(iterator -> {
                if (iterator.hasNext()) return MAPPER.apply(iterator.next());
                throw new PostNotFoundException(id);
            });
    }

    public Future<List<PostEntity>> findByAuthorId(UUID id) {
        Objects.requireNonNull(id, "id can not be null");
        return client.preparedQuery("SELECT * FROM posts WHERE author_id=$1").execute(Tuple.of(id))
            .map(rs -> StreamSupport.stream(rs.spliterator(), false)
                .map(MAPPER)
                .collect(Collectors.toList())
            );
    }

    public Future<UUID> create(String title, String content, String status, UUID authorId) {
        return client.preparedQuery("INSERT INTO posts(title, content, status, author_id) VALUES ($1, $2, $3, $4) RETURNING (id)")
            .execute(Tuple.of(title, content, status, authorId))
            .map(rs -> rs.iterator().next().getUUID("id"));
    }

    public Future<Integer> saveAll(List<PostEntity> data) {
        var tuples = data.stream()
            .map(
                d -> Tuple.of(d.title(), d.content())
            )
            .collect(Collectors.toList());

        return client.preparedQuery("INSERT INTO posts (title, content) VALUES ($1, $2)")
            .executeBatch(tuples)
            .map(SqlResult::rowCount);
    }

    public Future<Integer> update(PostEntity data) {
        return client.preparedQuery("UPDATE posts SET title=$1, content=$2 WHERE id=$3")
            .execute(Tuple.of(data.title(), data.content(), data.id()))
            .map(SqlResult::rowCount);
    }

    public Future<Integer> deleteAll() {
        return client.query("DELETE FROM posts").execute()
            .map(SqlResult::rowCount);
    }

    public Future<Integer> deleteById(UUID id) {
        Objects.requireNonNull(id, "id can not be null");
        return client.preparedQuery("DELETE FROM posts WHERE id=$1").execute(Tuple.of(id))
            .map(SqlResult::rowCount);
    }

}
