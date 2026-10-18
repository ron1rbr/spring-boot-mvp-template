package com.example.template.common;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests that need a real PostgreSQL instance.
 *
 * One container for the whole JVM (singleton pattern): started once in the
 * static initializer and never stopped explicitly; Testcontainer's Ryuk
 * sidecar removes it when the JVM exits. This matters because Spring caches
 * application contexts for the whole test run; a container tied to a single
 * test class would die while the cached context still points at it.
 *
 * Because the database is shared, every test starts from empty tables (see
 * truncateAllTables). Tests must create their own data and must never depend
 * on rows left behind by another test.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    POSTGRES.start();
  }

  @Autowired
  private JdbcTemplate jdbcTemplate;

  /**
   * Discovers tables dynamically instead of hardcoding a list, so new
   * Flyway migration never requires touching this class. Flyway's own
   * history table is preserved; wiping it would make the next context
   * try to re-run every migration.
   */
  @BeforeEach
  protected void truncateAllTables() {
    List<String> tables = jdbcTemplate.queryForList(
        "SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'",
        String.class);

    if (tables.isEmpty()) {
      return;
    }

    String quoted = tables.stream().map(table -> "\"" + table + "\"").collect(Collectors.joining(", "));

    jdbcTemplate.execute("TRUNCATE TABLE " + quoted + " CASCADE");
  }

}
