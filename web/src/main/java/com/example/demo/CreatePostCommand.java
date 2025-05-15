package com.example.demo;

import java.util.Objects;

public record CreatePostCommand(String title, String content) {
    public CreatePostCommand {
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(content, "content must not be null");
    }

    public static CreatePostCommand of(String title, String content) {
        return new CreatePostCommand(title, content);
    }
}

