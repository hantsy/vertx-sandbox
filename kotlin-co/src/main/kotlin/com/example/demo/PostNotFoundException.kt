package com.example.demo

import java.util.*

class PostNotFoundException(id: UUID) : RuntimeException("Post id: $id was not found. ")
