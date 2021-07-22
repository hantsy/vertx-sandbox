# Integrating Vertx application with  Weld/CDI

In [the last post](./spring.md),  we introduced a simple approach to integrate Vertx applications with Spring framework.  In this post,  we will try to integrate Vertx application with [CDI](https://www.cdi-spec.org) to replace Spring.

> CDI is a Dependency and Injection specification which is introduced in Java EE 6, currently Java EE is renamed to Jakarta EE and maintained by Eclipse Foundation.  Weld is a compatible provider of the Jakarta CDI specification. 

Add  the following dependencies.

```xml
<dependency>
    <groupId>org.jboss.weld.se</groupId>
    <artifactId>weld-se-shaded</artifactId>
    <version>${weld.version}</version>
</dependency>
<dependency>
    <groupId>org.jboss</groupId>
    <artifactId>jandex</artifactId>
    <version>2.2.3.Final</version>
</dependency>
```

In the above codes:

* The `weld-se-shaded` provides a CDI runtime environment for Java SE platform. 
* The `jandex` will index the classes in the application and speed up the bean searching.


Add an empty `beans.xml` configuration in the *main/resources/META-INF* folder which is to enable CDI support in a Java SE application.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans
        xmlns="http://xmlns.jcp.org/xml/ns/javaee"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/javaee
                      http://xmlns.jcp.org/xml/ns/javaee/beans_2_0.xsd"
        bean-discovery-mode="annotated">
</beans>
```
> In the Java EE/Jakarta EE world, CDI is enabled by default since Java EE 7, and the beans.xml configuration file is optional.

Similar to the Spring version, add a `DemoApplication` to start the application.

```java
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
```

Here we uses `weld.initialize()` to initialize the CDI container. Then retrieve the `Vertx` bean and `VerticleFactory` bean, and start to deploy the `MainVerticle`.

Similar to the `SpringAwareVerticleFactory` , create a CDI aware `VerticleFactory`.

```java
@ApplicationScoped
public class CdiAwareVerticleFactory implements VerticleFactory {
    
    @Inject
    private Instance<Object> instance;

    @Override
    public String prefix() {
        return "cdi";
    }

    @Override
    public void createVerticle(String verticleName, ClassLoader classLoader, Promise<Callable<Verticle>> promise) {
        String clazz = VerticleFactory.removePrefix(verticleName);
        promise.complete(() -> (Verticle) instance.select(Class.forName(clazz)).get());
    }
}
```

Create a simple `Resources`  classes and expose the `Vertx` and `PgPool` beans.

```java
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
    public PgPool pgPool(Vertx vertx) {
        PgConnectOptions connectOptions = new PgConnectOptions()
            .setPort(5432)
            .setHost("localhost")
            .setDatabase("blogdb")
            .setUser("user")
            .setPassword("password");

        // Pool Options
        PoolOptions poolOptions = new PoolOptions().setMaxSize(5);

        // Create the pool from the data object
        PgPool pool = PgPool.pool(vertx, connectOptions, poolOptions);

        return pool;
    }

    public void disposesPgPool(@Disposes PgPool pgPool) {
        LOGGER.info("disposing PgPool...");
        pgPool.close().onSuccess(v -> LOGGER.info("PgPool is closed successfully."));
    }
}
```

Other beans are similar to the Spring version, but using CDI `@ApplicaitonScoped` to replace the Spring `@Component`.

```java
@ApplicationScoped
@RequiredArgsConstructor
public class MainVerticle extends AbstractVerticle {
    final PostsHandler postHandlers;
    
    //...
}
```

```java
@ApplicationScoped
@RequiredArgsConstructor
class PostsHandler {
    private final PostRepository posts;
    
    //...
}
```

```java
@ApplicationScoped
@RequiredArgsConstructor
public class PostRepository {

    private final PgPool client;
    
    //...
}
```

```java
@ApplicationScoped
@RequiredArgsConstructor
public class DataInitializer {

    private  final PgPool client;
    
    //...
}
```

Please note, in the above `Resources` class, we add a `@Singleton` to the `Vertx`, which is a little different from the `ApplicationScoped`, Weld does not create a  proxy object for it. 

>Here we have to add `@Singleton` on Vertx bean, else there is an error of casting to `VertxImpl` at the application startup stage, because CDI does not create a proxy bean for `VertxImpl`.

In the `DemoApplication`, we have added some log to print the class name of `Vertx` and `VerticleFactory` beans.  When starting the application, in the console, you will see the class names as the following.

```bash
//..
INFO: vertx clazz:io.vertx.core.impl.VertxImpl
//...
INFO: factory clazz:com.example.demo.CdiAwareVerticleFactory$Proxy$_$$_WeldClientProxy
```

By default CDI will create proxy classes for all beans, but if it is annotated with `@Singletone`, it will use the instance directly.

To test the application, add the following dependency to *test* scope.

```xml
<dependency>
    <groupId>org.jboss.weld</groupId>
    <artifactId>weld-junit5</artifactId>
    <version>2.0.2.Final</version>
    <scope>test</scope>
    <exclusions>
        <exclusion>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-api</artifactId>
        </exclusion>
        <exclusion>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-engine</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

Similar to the Spring version, manually deploy the `Verticle` in the JUnit `@BeforeAll` hook before running tests.

```java
@EnableAutoWeld
@AddPackages(DemoApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(VertxExtension.class)
public class TestMainVerticle {
    private final static Logger LOGGER = Logger.getLogger(TestMainVerticle.class.getName());

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
            });
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
```

Get the [example codes from my github](https://github.com/hantsy/vertx-sandbox/tree/master/post-service-cdi).

