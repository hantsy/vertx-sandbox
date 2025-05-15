package com.example.demo;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Objects;

public record Post(UUID id, String title, String content, LocalDateTime createdAt) {

    public Post {
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(content, "content must not be null");
    }

    public static Post of(String title, String content) {
        return new Post(null, title, content, null);
    }
}
