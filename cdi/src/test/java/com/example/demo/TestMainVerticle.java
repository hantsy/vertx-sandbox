package com.example.demo;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.spi.VerticleFactory;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.weld.junit5.ExplicitParamInjection;
import org.jboss.weld.junit5.auto.AddPackages;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.logging.Level;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

@EnableAutoWeld
@ExplicitParamInjection
@AddPackages(DemoApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(VertxExtension.class)
public class TestMainVerticle {
    private static final Logger LOGGER = Logger.getLogger(TestMainVerticle.class.getName());

    @Inject
    Instance<Object> context;

    Vertx vertx;

    @BeforeAll
    public void setupAll(VertxTestContext testContext) {
        vertx = context.select(Vertx.class).get();
        var factory = context.select(VerticleFactory.class).get();
        vertx.deployVerticle(factory.prefix() + ":" + MainVerticle.class.getName())
            .onSuccess(id -> {
                LOGGER.info("deployed:" + id);
                testContext.completeNow();
            })
            .onFailure(testContext::failNow);
    }

    @Test
    public void testVertx(VertxTestContext testContext) {
        assertThat(vertx).isNotNull();
        testContext.completeNow();
    }


    @Test
    void testGetAll(VertxTestContext testContext) {
        LOGGER.log(Level.INFO, "running test: {0}", "testGetAll");
        var options = new HttpClientOptions()
            .setDefaultPort(8888);
        var client = vertx.createHttpClient(options);

        client.request(HttpMethod.GET, "/posts")
            .flatMap(req -> req.send().flatMap(HttpClientResponse::body))
            .onSuccess(
                buffer -> testContext.verify(
                    () -> {
                        LOGGER.log(Level.INFO, "response buffer: {0}", new Object[]{buffer.toString()});
                        assertThat(buffer.toJsonArray().size()).isGreaterThan(0);
                        testContext.completeNow();
                    }
                )
            )
            .onFailure(e -> LOGGER.log(Level.ALL, "error: {0}", e.getMessage()));
    }

}
