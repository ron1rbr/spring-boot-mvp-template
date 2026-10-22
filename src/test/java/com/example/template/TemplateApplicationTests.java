package com.example.template;

import org.junit.jupiter.api.Test;

import com.example.template.common.AbstractIntegrationTest;

class TemplateApplicationTests extends AbstractIntegrationTest {

  @Test
  void contextLoads() {
    // Fails if the Spring context can't start: bad configuration, a
    // missing bean, or a Flyway migration that doesn't apply cleanly.
    // Extends AbstractIntegrationTest so it never depends on a database
    // running on the developer's machine.
  }

}
