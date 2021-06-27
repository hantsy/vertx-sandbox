package com.example.demo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.Objects;

@Data
@AllArgsConstructor(staticName = "of")
public class CreatePostCommand implements Serializable {
    String title;
    String content;
}
