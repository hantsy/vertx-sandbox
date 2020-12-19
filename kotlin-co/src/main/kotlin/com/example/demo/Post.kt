package com.example.demo

import java.time.LocalDateTime
import java.util.*

data class Post(
    var id: UUID? = null,
    var title: String,
    var content: String,
    var createdAt: LocalDateTime? = LocalDateTime.now()
)
