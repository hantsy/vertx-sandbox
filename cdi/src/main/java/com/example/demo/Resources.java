package com.example.demo;

import io.vertx.core.Vertx;
import io.vertx.core.spi.VerticleFactory;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import java.util.logging.Logger;

@ApplicationScoped
public class Resources {
    private final static Logger LOGGER = Logger.getLogger(Resources.class.getName());

    @Produces
    @Singleton
    public Vertx vertx(VerticleFactory verticleFactory) {
        Vertx vertx = Vertx.vertx();
        vertx.registerVerticleFactory(verticleFactory);
        return vertx;
    }

    @Produces
    public Pool pgPool(Vertx vertx) {
        PgConnectOptions connectOptions = new PgConnectOptions()
            .setPort(5432)
            .setHost("localhost")
            .setDatabase("blogdb")
            .setUser("user")
            .setPassword("password");

        // Pool Options
        PoolOptions poolOptions = new PoolOptions().setMaxSize(5);

        // Create the pool from the data object
        return Pool.pool(vertx, connectOptions, poolOptions);
    }

    public void disposesPgPool(@Disposes Pool pgPool) {
        LOGGER.info("disposing PgPool...");
        pgPool.close().onSuccess(v -> LOGGER.info("PgPool is closed successfully."));
    }
}
