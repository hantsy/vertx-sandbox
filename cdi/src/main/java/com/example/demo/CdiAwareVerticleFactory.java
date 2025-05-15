package com.example.demo;

import io.vertx.core.Deployable;
import io.vertx.core.Promise;
import io.vertx.core.Verticle;
import io.vertx.core.VerticleBase;
import io.vertx.core.spi.VerticleFactory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.concurrent.Callable;

// see: https://github.com/vert-x3/vertx-examples/blob/4.x/spring-examples/spring-verticle-factory/src/main/java/io/vertx/examples/spring/verticlefactory/SpringVerticleFactory.java
@ApplicationScoped
public class CdiAwareVerticleFactory implements VerticleFactory {

    @Inject
    private Instance<Object> instance;

    @Override
    public String prefix() {
        return "cdi";
    }

    @Override
    public void createVerticle2(String verticleName, ClassLoader classLoader, Promise<Callable<? extends Deployable>> promise) {
        String clazz = VerticleFactory.removePrefix(verticleName);
        promise.complete(() -> (VerticleBase) instance.select(Class.forName(clazz)).get());
    }
}
