package com.example.demo;

import java.io.Serializable;

public record CreatePostCommand(String title, String content) {}
