package com.example.demo;

import io.vertx.core.Vertx;
import io.vertx.core.spi.VerticleFactory;
import org.jboss.weld.environment.se.Weld;

public class DemoApplication {

    public static void main(String[] args) {
        var weld = new Weld();
        var container = weld.initialize();
        var vertx = container.select(Vertx.class).get();
        var factory = container.select(VerticleFactory.class).get();

        // deploy MainVerticle via verticle identifier name
        //var deployOptions = new DeploymentOptions().setInstances(4);
        vertx.deployVerticle(factory.prefix() + ":" + MainVerticle.class.getName());
    }
}
