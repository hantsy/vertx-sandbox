# Migrating to Jackson 3

Vert.x 5.2+ can use **Jackson 3** (`tools.jackson` namespace) as its JSON backend instead of the default Jackson 2 (`com.fasterxml.jackson`). All subprojects of this sandbox have been migrated to Jackson 3 (using `tools.jackson:jackson-bom` 3.1.4).

## How Vert.x picks the JSON backend

Vert.x resolves a `JsonFactory` via the Java `ServiceLoader` (`META-INF/services/io.vertx.core.spi.JsonFactory`), then falls back to built-in implementations. The built-in Jackson 3 support ships as a multi-release JAR entry in `vertx-core` (`io.vertx.core.json.jackson.v3.*`, requires Java 21+):

- If `com.fasterxml.jackson.core:jackson-core` **is on** the classpath, Vert.x uses the Jackson 2 codec.
- If Jackson 2 `jackson-core` is **absent** and `tools.jackson.core:jackson-databind` is present, Vert.x activates the Jackson 3 `DatabindCodec`.

So the migration is essentially a dependency-shading exercise: keep Jackson 2 off the classpath, add Jackson 3.

## Dependency setup

Because Vert.x modules declare a hard dependency on Jackson 2 `jackson-core`, exclude it centrally. Putting the exclusion on a single managed `vertx-core` entry makes every Vert.x artifact resolved through the BOM inherit it:

```xml
<properties>
    <jackson3.version>3.1.4</jackson3.version>
</properties>

<dependencyManagement>
    <dependencies>
        <!-- exclude jackson-core via https://github.com/vert-x3/wiki/issues/20 -->
        <dependency>
            <groupId>io.vertx</groupId>
            <artifactId>vertx-core</artifactId>
            <version>${vertx.version}</version>
            <exclusions>
                <exclusion>
                    <groupId>com.fasterxml.jackson.core</groupId>
                    <artifactId>jackson-core</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
        <dependency>
            <groupId>tools.jackson</groupId>
            <artifactId>jackson-bom</artifactId>
            <version>${jackson3.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>io.vertx</groupId>
            <artifactId>vertx-stack-depchain</artifactId>
            <version>${vertx.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Then add the Jackson 3 databind module as a normal dependency:

```xml
<dependency>
    <groupId>tools.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

!!! note
    The old `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` and `jackson-datatype-jdk8` dependencies are no longer needed — Jackson 3 has built-in `java.time` and `java.util.Optional` support and ISO-8601 date formats are the default.

## Code changes

Jackson 3 mappers are **immutable**; modules must be registered when the mapper is built. The Vert.x 4/early-5 pattern of customizing the shared mapper at startup:

```java
// Jackson 2 era — no longer works
ObjectMapper mapper = DatabindCodec.mapper();
mapper.registerModule(new JavaTimeModule());
```

is no longer required: date/time handling works out of the box, and Vert.x registers its own `VertxModule` automatically.

!!! tip
    After migrating, start the application and check the logs. Vert.x prints which codec it activated, e.g.:

    ```text
    Using Jackson 3 DatabindCodec
    ```

## Kotlin projects

The Kotlin subprojects (`kotlin`, `kotlin-co`) need `KotlinModule` registered on the mapper used by `io.vertx.core.json.Json` — otherwise `Json.decodeValue(buffer, Post.class)` fails on data classes. Since Jackson 3 mappers are immutable and Vert.x builds the mapper itself, the migration registers a custom `JsonFactory` SPI implementation:

- `src/main/kotlin/com/example/demo/KotlinJsonFactory.kt` builds a `JsonMapper` with `VertxModule` + `KotlinModule` and exposes a shared `MAPPER`, wrapped in a Vert.x `JsonCodec`.
- `src/main/resources/META-INF/services/io.vertx.core.spi.JsonFactory` points to it.
- `VertxModule` lives in a multi-release JAR entry that the Kotlin compiler cannot see, so it is loaded reflectively at runtime.

Application code then simply uses `Json.encodePrettily(...)` / `Json.decodeValue(...)` as before, and tests deserialize with `KotlinJsonFactory.MAPPER.readValue(body, jacksonTypeRef<List<Post>>())`.

## Notes on this repository

| Subproject | Jackson 3 artifacts used |
|---|---|
| web, rxjava3, cdi, spring, graphql-http, graphql-transport-ws, mutiny-spring-hibernate | `tools.jackson.core:jackson-databind` |
| kotlin, kotlin-co | additionally `tools.jackson.module:jackson-module-kotlin` + custom `JsonFactory` SPI |

`mutiny-spring-hibernate` needs no explicit `jackson-bom` import either — its Spring Boot BOM already manages the Jackson version line.
