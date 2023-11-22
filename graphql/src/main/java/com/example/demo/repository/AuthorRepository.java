package com.example.demo.repository;

import com.example.demo.model.AuthorEntity;
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
public class AuthorRepository {
    private static Function<Row, AuthorEntity> MAPPER = (row) -> new AuthorEntity(
        row.getUUID("id"),
        row.getString("name"),
        row.getString("email"),
        row.getLocalDateTime("created_at")
    );

    private final Pool client;

    public Future<List<AuthorEntity>> findAll() {
        return client.query("SELECT * FROM users ORDER BY created_at DESC ")
            .execute()
            .map(rs -> StreamSupport.stream(rs.spliterator(), false)
                .map(MAPPER)
                .toList()
            );
    }

    public Future<Integer> deleteAll() {
        return client.query("DELETE FROM users").execute()
            .map(SqlResult::rowCount);
    }


    public Future<AuthorEntity> findById(UUID id) {
        Objects.requireNonNull(id, "id can not be null");
        return client.preparedQuery("SELECT * FROM users WHERE id=$1").execute(Tuple.of(id))
            .map(RowSet::iterator)
            .map(iterator -> {
                if (iterator.hasNext()) return MAPPER.apply(iterator.next());
                throw new AuthorNotFoundException(id);
            });
    }


    public Future<List<AuthorEntity>> findByIdIn(List<UUID> uuids) {
        return client.preparedQuery("SELECT * FROM users WHERE id = any($1)").execute(Tuple.of(uuids.toArray(new UUID[0])))
            .map(rs -> StreamSupport.stream(rs.spliterator(), false)
                .map(MAPPER)
                .toList()
            );
    }

    public Future<UUID> create(String name, String email) {
        return client.preparedQuery("INSERT INTO users(name, email) VALUES ($1, $2) RETURNING (id)")
            .execute(Tuple.of(name, email))
            .map(rs -> rs.iterator().next().getUUID("id"));
    }
}
