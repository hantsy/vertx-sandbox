package com.example.demo.repository;

import com.example.demo.model.CommentEntity;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlResult;
import io.vertx.sqlclient.Tuple;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.StreamSupport;

@RequiredArgsConstructor
public class CommentRepository {
    private static Function<Row, CommentEntity> MAPPER = (row) -> new CommentEntity(
        row.getUUID("id"),
        row.getString("content"),
        row.getLocalDateTime("created_at"),
        row.getUUID("post_id")
    );

    private final PgPool client;

    public Future<List<CommentEntity>> findAll() {
        return client.query("SELECT * FROM comments ORDER BY id ASC")
            .execute()
            .map(rs -> StreamSupport.stream(rs.spliterator(), false)
                .map(MAPPER)
                .toList()
            );
    }

    public Future<Integer> deleteAll() {
        return client.query("DELETE FROM comments").execute()
            .map(SqlResult::rowCount);
    }


    public Future<List<CommentEntity>> findByPostIdIn(List<UUID> uuids) {
        return client.preparedQuery("SELECT * FROM comments WHERE post_id = any($1)").execute(Tuple.of(uuids.toArray(new UUID[0])))
            .map(rs -> StreamSupport.stream(rs.spliterator(), false)
                .map(MAPPER)
                .toList()
            );
    }

    public Future<List<CommentEntity>> findByPostId(UUID id) {
        return client.preparedQuery("SELECT * FROM comments WHERE post_id=$1").execute(Tuple.of(id))
            .map(rs -> StreamSupport.stream(rs.spliterator(), false)
                .map(MAPPER)
                .toList()
            );
    }

    public Future<UUID> create(String content, UUID postId) {
        return client.preparedQuery("INSERT INTO comments(content, post_id) VALUES ($1, $2) RETURNING (id)")
            .execute(Tuple.of(content, postId))
            .map(rs -> rs.iterator().next().getUUID("id"));
    }
}
