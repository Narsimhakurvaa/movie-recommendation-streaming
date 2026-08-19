package com.cinevault.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Base class for tests that need genuine PostgreSQL behaviour.
 *
 * <p>H2 in PostgreSQL mode is fine for wiring tests, but it does not run the
 * triggers, partial indexes or expression-based unique indexes this schema
 * relies on. Anything asserting those must run against the real engine.
 *
 * <p>The container is {@code static} and deliberately not declared with
 * {@code @Container}, so a single instance is reused across every subclass for
 * the whole test run rather than being restarted per class.
 */
@SpringBootTest
@ActiveProfiles("integration")
@Testcontainers
@Tag("integration")
public abstract class PostgresIntegrationTest {

    @SuppressWarnings("resource") // shared for the lifetime of the JVM
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("cinevault")
                    .withUsername("cinevault")
                    .withPassword("cinevault")
                    .withReuse(true);

    static {
        POSTGRES.start();
    }

    /**
     * Points the context at the container and lets Flyway build the schema, so
     * the migrations themselves are exercised on every integration run.
     */
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    /** Marks a test class as requiring the shared PostgreSQL container. */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    public @interface RequiresPostgres {
    }
}
