package com.example.demo

import io.vertx.ext.web.Route
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

suspend fun Route.coroutineHandler(fn: suspend (RoutingContext) -> Unit): Route = coroutineScope {
    handler {
        launch(it.vertx().dispatcher()) {
            try {
                fn(it)
            } catch (e: Exception) {
                it.fail(e)
            }
        }
    }
}

