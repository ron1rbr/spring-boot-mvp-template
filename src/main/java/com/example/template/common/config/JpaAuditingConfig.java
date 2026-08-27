package com.example.template.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables @CreatedDate / @LastModifiedDate support across all entities.
 * Isolated in its own config class (rather than on the main application class)
 * so it's discoverable by anyone searching common/config for cross-cutting JPA
 * behavior.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
