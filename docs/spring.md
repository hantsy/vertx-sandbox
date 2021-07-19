# Integrating Vertx application with  Spring framework

As shown in the former we assembled everything manually in the `MainVerticle` class.  

For example, it looks like the following.

```java
//Create a PgPool instance
var pgPool = pgPool();

//Creating PostRepository
var postRepository = PostRepository.create(pgPool);

//Creating PostHandler
var postHandlers = PostsHandler.create(postRepository);

// Initializing the sample data
var initializer = DataInitializer.create(pgPool);
initializer.run();

// Configure routes
var router = routes(postHandlers);

// Create the HTTP server
vertx.createHttpServer()...
```

In this post, we will introduce Spring framework to manage the dependencies of the above items. 

Add the Spring context dependency into the project *pom.xml* file.

```xml
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-context</artifactId>
</dependency>
```

The `spring-context` provides basic IOC functionality which is used for assembling dependencies and providing dependency injection.

Create a `@Configuration` class to start Spring application context.

```java
@Configuration
@ComponentScan
public class DemoApplication {

    public static void main(String[] args) {
        var context = new AnnotationConfigApplicationContext(DemoApplication.class);
        var vertx = context.getBean(Vertx.class);
        var factory  = context.getBean(VerticleFactory.class);

        // deploy MainVerticle via verticle identifier name
        vertx.deployVerticle(factory.prefix()+":"+MainVerticle.class.getName());
    }

    @Bean
    public Vertx vertx(VerticleFactory verticleFactory) {
        Vertx vertx = Vertx.vertx();
        vertx.registerVerticleFactory(verticleFactory);
        return vertx;
    }

    @Bean
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
}
```

In the main method, we use a `AnnotationConfigApplicationContext` to scan Spring components and assemble the dependencies. Then fetch `Vertx`  bean and `VerticleFactory` bean from the Spring application context,  and  call `vertx.deployVerticle`.  

> The `VerticleFactory` is a Vertx built-in hook for  instantiating a Verticle instance.

In this configuration,  we also declares beans:

*  `Vertx`  which accepts `VerticleFactory` bean and  registers a `VerticleFactory` in the `Vertx` bean.
* `PgPool` is used for accessing Postgres database.  You can move the database configuration to a *properties* file, and load it by Spring `@PropertySource` annotation. 

Let's have a look at  `VerticleFactory`  bean - a Spring context aware VerticleFactory implementation.

```java
@Component
public class SpringAwareVerticleFactory implements VerticleFactory, ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Override
    public String prefix() {
        return "spring";
    }

    @Override
    public void createVerticle(String verticleName, ClassLoader classLoader, Promise<Callable<Verticle>> promise) {
        String clazz = VerticleFactory.removePrefix(verticleName);
        promise.complete(() -> (Verticle) applicationContext.getBean(Class.forName(clazz)));
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
```

When instantiating a `Verticle` by name, it will call the `createVerticle` method, which looks up the beans in the Spring application context.

Let's have a look at the other classes, which are declared as Spring `@Component` directly.  For the complete source codes, check  [vertx-sandbox/post-service-spring](https://github.com/hantsy/vertx-sandbox/tree/master/post-service-spring) from my Github.

```java
@Component
@RequiredArgsConstructor
public class MainVerticle extends AbstractVerticle {
    final PostsHandler postHandlers;
    
    //...
}
```

```java
@Component
@RequiredArgsConstructor
class PostsHandler {
    private final PostRepository posts;
    
    //...
}
```

```java
@Component
@RequiredArgsConstructor
public class PostRepository {

    private final PgPool client;
    
    //...
}
```

```java
@Component
@RequiredArgsConstructor
public class DataInitializer {

    private  final PgPool client;
    
    //...
}
```

All `@Component`s can be scanned when `DemoApplicaiton` is running. 

As you see, through Spring IOC container, we erase all manual steps of the assembling the dependencies.

Now let's try to start the application. 

In the former application, the application can be run by maven exec plugin and jar file. But it uses a built-in `Launcher` class to deploy the `Verticle`.  We have configured to use Spring to complete the same work, so change the configuration of maven exec plugin and  maven shade plugin to the following.

```xml
<plugin>
    <artifactId>maven-shade-plugin</artifactId>
    <version>${maven-shade-plugin.version}</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>shade</goal>
            </goals>
            <configuration>
                <transformers>
                    <transformer
                                 implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                        <manifestEntries>
                            <Main-Class>com.example.demo.DemoApplication</Main-Class>
                        </manifestEntries>
                    </transformer>
                    <transformer
                                 implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
                </transformers>
                <artifactSet>
                </artifactSet>
                <outputFile>${project.build.directory}/${project.artifactId}-${project.version}-fat.jar
                </outputFile>
            </configuration>
        </execution>
    </executions>
</plugin>
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <version>${exec-maven-plugin.version}</version>
    <configuration>
        <mainClass>com.example.demo.DemoApplication</mainClass>
    </configuration>
</plugin>
```

Now run the following command to start the application.

```bash
mvn clean compile exec:java

//or
mvn clean package
java -jar target\xxx-fat.jar
```

In the `TestMainVerticle`, let's do some modifications to use Spring IOC container, eg. you can get a `Vertx` instance from the Spring application context.

```java
@SpringJUnitConfig(classes = DemoApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(VertxExtension.class)
public class TestMainVerticle {
    private final static Logger LOGGER = Logger.getLogger(TestMainVerticle.class.getName());

    @Autowired
    ApplicationContext context;

    Vertx vertx;

    @BeforeAll
    public void setupAll(VertxTestContext testContext) {
        vertx = context.getBean(Vertx.class);
        var factory = context.getBean(VerticleFactory.class);
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
            .onFailure(e -> {
                LOGGER.log(Level.ALL, "error: {0}", e.getMessage());
                testContext.failNow(e);
            });
    }
}
```

 Get the [example codes from my github](https://github.com/hantsy/vertx-sandbox/tree/master/rxjava3).

