package com.example.demo.service;

import com.example.demo.gql.types.Author;
import com.example.demo.model.AuthorEntity;
import com.example.demo.repository.AuthorRepository;
import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

@RequiredArgsConstructor
public class AuthorService {
    private static final Function<AuthorEntity, Author> MAPPER = a -> Author.builder()
        .id(a.id().toString())
        .name(a.name())
        .email(a.email())
        .createdAt(a.createdAt())
        .build();
    final AuthorRepository authors;

    public Future<Author> getAuthorById(String id) {
        var authorEntity = this.authors.findById(UUID.fromString(id));
        return authorEntity.map(MAPPER);
    }

    public Future<List<Author>> getAuthorByIdIn(Collection<String> ids) {
        var uuids = ids.stream().map(UUID::fromString).toList();
        var authorEntities = this.authors.findByIdIn(uuids);
        return authorEntities.map(
            entities -> entities.stream().map(MAPPER).toList()
        );
    }
}

