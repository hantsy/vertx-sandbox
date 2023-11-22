package com.example.demo.repository;

import com.example.demo.model.CommentEntity;
import com.example.demo.model.PostEntity;
import io.vertx.core.Future;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.*;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Objects;
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

    private final Pool client;

    public Future<List<CommentEntity>> findAll() {
        return client.query("SELECT * FROM comments ORDER BY created_at DESC ")
            .execute()
            .map(rs -> StreamSupport.stream(rs.spliterator(), false)
                .map(MAPPER)
                .toList()
            );
    }

    public Future<CommentEntity> findById(UUID id) {
        Objects.requireNonNull(id, "id can not be null");
        return client.preparedQuery("SELECT * FROM comments WHERE id=$1").execute(Tuple.of(id))
            .map(RowSet::iterator)
            .map(iterator -> {
                if (iterator.hasNext()) return MAPPER.apply(iterator.next());
                throw new CommentNotFoundException(id);
            });
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
