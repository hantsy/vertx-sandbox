package com.example.demo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor(staticName = "of")
@Builder
public class Post {
    UUID id;
    String title;
    String content;
    LocalDateTime createdAt;
}
