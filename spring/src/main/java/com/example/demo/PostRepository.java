package com.example.demo;

import io.vertx.core.Future;
import io.vertx.sqlclient.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.StreamSupport;

@Component
public class PostRepository {
    private static final Logger LOGGER = Logger.getLogger(PostRepository.class.getName());

    private static Function<Row, Post> MAPPER = (row) ->
        new Post(
            row.getUUID("id"),
            row.getString("title"),
            row.getString("content"),
            row.getLocalDateTime("created_at")
        );


    private final Pool client;

    public PostRepository(Pool client) {
        this.client = client;
    }

    public Future<List<Post>> findAll() {
        String sql = "SELECT * FROM posts ORDER BY id ASC";
        return client.query(sql)
            .execute()
            .map(rs -> StreamSupport.stream(rs.spliterator(), false)
                .map(MAPPER)
                .toList()
            );
    }


    public Future<Post> findById(UUID id) {
        Objects.requireNonNull(id, "id can not be null");
        String sql = "SELECT * FROM posts WHERE id=$1";
        return client.preparedQuery(sql).execute(Tuple.of(id))
            .map(RowSet::iterator)
            .map(iterator -> {
                if (iterator.hasNext()) {
                    return MAPPER.apply(iterator.next());
                }
                throw new PostNotFoundException(id);
            });
    }

    public Future<UUID> save(Post data) {
        String sql = "INSERT INTO posts(title, content) VALUES ($1, $2) RETURNING (id)";
        return client.preparedQuery(sql)
            .execute(Tuple.of(data.title(), data.content()))
            .map(rs -> rs.iterator().next().getUUID("id"));
    }

    public Future<Integer> saveAll(List<Post> data) {
        var tuples = data.stream()
            .map(
                d -> Tuple.of(d.title(), d.content())
            )
            .toList();

        String sql = "INSERT INTO posts (title, content) VALUES ($1, $2)";
        return client.preparedQuery(sql)
            .executeBatch(tuples)
            .map(SqlResult::rowCount);
    }

    public Future<Integer> update(Post data) {
        String sql = "UPDATE posts SET title=$1, content=$2 WHERE id=$3";
        return client.preparedQuery(sql)
            .execute(Tuple.of(data.title(), data.content(), data.id()))
            .map(SqlResult::rowCount);
    }

    public Future<Integer> deleteAll() {
        String sql = "DELETE FROM posts";
        return client.query(sql).execute()
            .map(SqlResult::rowCount);
    }

    public Future<Integer> deleteById(UUID id) {
        Objects.requireNonNull(id, "id can not be null");
        String sql = "DELETE FROM posts WHERE id=$1";
        return client.preparedQuery(sql).execute(Tuple.of(id))
            .map(SqlResult::rowCount);
    }

}
