package com.example.demo;

import io.vertx.core.spi.VerticleFactory;
import io.vertx.mutiny.core.Vertx;
import org.hibernate.reactive.mutiny.Mutiny;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import javax.persistence.Persistence;

@Configuration
@ComponentScan
public class DemoApplication {

    public static void main(String[] args) {
        var context = new AnnotationConfigApplicationContext(DemoApplication.class);
        var vertx = context.getBean(Vertx.class);
        var factory = context.getBean(VerticleFactory.class);

        // deploy MainVerticle via verticle identifier name
        vertx.deployVerticleAndAwait(factory.prefix() + ":" + MainVerticle.class.getName());
    }

    @Bean
    public Vertx vertx(VerticleFactory verticleFactory) {
        Vertx vertx = Vertx.vertx();
        vertx.registerVerticleFactory(verticleFactory);
        return vertx;
    }

    @Bean
    public Mutiny.SessionFactory sessionFactory() {
        return Persistence.createEntityManagerFactory("blogPU")
            .unwrap(Mutiny.SessionFactory.class);
    }
}
