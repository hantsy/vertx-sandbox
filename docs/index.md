# Eclipse Vert.x Sandbox

A multi-module project demonstrating various Eclipse Vert.x 5 features, including RESTful APIs, reactive data access, GraphQL, and framework integrations.

## Tech Stack

| Technology | Version |
|---|---|
| Vert.x | 5.1.3 |
| Java | 25 |
| Kotlin | 2.4.0 |
| Jackson | 2.22.0 |
| JUnit | 6.0.2 |
| Maven Shade Plugin | 3.6.1 |
| Maven Surefire | 3.5.4 |
| Exec Maven | 3.6.3 |

## Prerequisites

- **Java 25** (set via `maven.compiler.release=25`)
- **Apache Maven** (3.9+)
- **Docker** (for running PostgreSQL locally)

## Quick Start

1. Start a PostgreSQL instance from the project root:

```bash
docker compose up postgres
```

2. Build and run a specific module:

```bash
cd spring
mvn clean compile exec:java
```

Or package and run the fat JAR:

```bash
mvn clean package
java -jar target/xxx-fat.jar
```

## Subprojects

### Data Layer

- [Building RESTful APIs with Eclipse Vert.x](./data/rest.md) — REST API with Vert.x Web, PgPool, and SQL client
- [Building RESTful APIs with Eclipse Vert.x and RxJava 3](./data/rxjava3.md) — RxJava 3 bindings for reactive data access
- [Building RESTful APIs with Eclipse Vert.x and Kotlin](./data/kotlin.md) — Kotlin language support
- [Building RESTful APIs with Eclipse Vert.x and Kotlin Coroutines](./data/kotlin-co.md) — Kotlin coroutines for async programming

### Web Layer

- [Consuming RESTful APIs with Vert.x HttpClient](./web/client.md) — HTTP client usage
- [Exception Handling and Validation](./web/validation.md) — Validation handlers
- [Building GraphQL APIs with Eclipse Vert.x](./web/graphql-http.md) — GraphQL over HTTP
- [Consuming GraphQL APIs with Vert.x WebClient](./web/graphql-client.md) — GraphQL client

### Integration

- [Integrating Vert.x Application with Spring Framework](./integration/spring.md) — Spring DI integration
- [Integrating Vert.x Application with Weld/CDI](./integration/cdi.md) — CDI integration
- [Building Vert.x Application with SmallRye Mutiny, Spring and Hibernate](./integration/mutiny-spring-hibernate.md) — Mutiny + Hibernate Reactive

