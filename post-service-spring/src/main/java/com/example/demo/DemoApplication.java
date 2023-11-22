package com.example.demo;

import io.vertx.core.Vertx;
import io.vertx.core.spi.VerticleFactory;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan
public class DemoApplication {

    public static void main(String[] args) {
        var context = new AnnotationConfigApplicationContext(DemoApplication.class);
        var vertx = context.getBean(Vertx.class);
        var factory = context.getBean(VerticleFactory.class);

        // deploy MainVerticle via verticle identifier name
        vertx.deployVerticle(factory.prefix() + ":" + MainVerticle.class.getName());
    }

    @Bean
    public Vertx vertx(VerticleFactory verticleFactory) {
        Vertx vertx = Vertx.vertx();
        vertx.registerVerticleFactory(verticleFactory);
        return vertx;
    }

    @Bean
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
}
