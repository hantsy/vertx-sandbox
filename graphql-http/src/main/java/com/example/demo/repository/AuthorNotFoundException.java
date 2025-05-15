package com.example.demo.repository;

import java.util.UUID;

public class AuthorNotFoundException extends RuntimeException {
    public AuthorNotFoundException(UUID id) {
        super("Author: " + id + " was not found.");
    }
}
