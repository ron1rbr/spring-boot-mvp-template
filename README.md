# spring-boot-mvp-tamplate

Reusable Spring Boot template for bootstrapping new MVPs quickly:
JWT auth with refresh token rotation, PostgreSQL + Testcontainers,
RFC 7807 error handling, and optional Oauth2 / rate limiting modules.

## Getting started

1. Use this repository as a GitHub template.
2. Find and replace `com.example.template` with your own package.
3. See `docker-compose.yml` (added in a later commit) to run PostgreSQL locally.

## Core vs optional

- `v1-core` tag: JWT + refresh token auth, error handling, persistence; the essentials.
- Commits after `v1-core`: Oauth2 login, Bucket4j rate limiting. cherry-pick as needed.