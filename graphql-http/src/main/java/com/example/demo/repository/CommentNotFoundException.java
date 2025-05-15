package com.example.demo.repository;

import java.util.UUID;

public class CommentNotFoundException extends RuntimeException {
    public CommentNotFoundException(UUID id) {
        super("Comment: " + id + " was not found.");
    }
}
