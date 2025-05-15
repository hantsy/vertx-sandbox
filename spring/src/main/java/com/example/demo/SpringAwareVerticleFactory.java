package com.example.demo;

import io.vertx.core.Deployable;
import io.vertx.core.Promise;
import io.vertx.core.Verticle;
import io.vertx.core.VerticleBase;
import io.vertx.core.spi.VerticleFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;

// see: https://github.com/vert-x3/vertx-examples/blob/4.x/spring-examples/spring-verticle-factory/src/main/java/io/vertx/examples/spring/verticlefactory/SpringVerticleFactory.java
@Component
public class SpringAwareVerticleFactory implements VerticleFactory, ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Override
    public String prefix() {
        return "spring";
    }

    @Override
    public void createVerticle2(String verticleName, ClassLoader classLoader, Promise<Callable<? extends Deployable>> promise) {
        String clazz = VerticleFactory.removePrefix(verticleName);
        promise.complete(() -> (VerticleBase) applicationContext.getBean(Class.forName(clazz)));
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
