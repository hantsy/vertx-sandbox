package com.example.demo;

import io.vertx.core.Vertx;
import io.vertx.core.spi.VerticleFactory;
import org.jboss.weld.environment.se.Weld;

import java.util.logging.Logger;

public class DemoApplication {

    private final static Logger LOGGER = Logger.getLogger(DemoApplication.class.getName());

    public static void main(String[] args) {
        var weld = new Weld();
        var container = weld.initialize();
        var vertx = container.select(Vertx.class).get();
        var factory = container.select(VerticleFactory.class).get();

        LOGGER.info("vertx clazz:" + vertx.getClass().getName());//Weld does not create proxy classes at runtime on @Singleton beans.
        LOGGER.info("factory clazz:" + factory.getClass().getName());
        // deploy MainVerticle via verticle identifier name
        //var deployOptions = new DeploymentOptions().setInstances(4);
        vertx.deployVerticle(factory.prefix() + ":" + MainVerticle.class.getName());
    }
}
