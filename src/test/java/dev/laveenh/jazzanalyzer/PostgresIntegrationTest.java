package dev.laveenh.jazzanalyzer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Base for tests that need the whole application against a real PostgreSQL.
 *
 * <p>One container is started once and shared by every test class (starting a database per class would
 * make the suite slow), and one temporary directory plays the part of file storage. Because every
 * subclass uses the same configuration, Spring also reuses a single application context.
 */
@SpringBootTest
public abstract class PostgresIntegrationTest {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    protected static final Path STORAGE_DIR;

    static {
        POSTGRES.start();
        try {
            STORAGE_DIR = Files.createTempDirectory("jazz-test-uploads");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("jazz.storage.local-dir", STORAGE_DIR::toString);
    }

    @Autowired
    protected JdbcTemplate jdbc;

    /** Tests start from an empty database. Children go too, through the ON DELETE CASCADE rules. */
    protected void clearDatabase() {
        jdbc.execute("TRUNCATE songs CASCADE");
    }
}
