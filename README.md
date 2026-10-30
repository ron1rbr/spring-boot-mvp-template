# spring-boot-mvp-template

A production-minded Spring Boot starter for new MVPs. Clone it, rename the
package, and start building features on top of authentication, error
handling, persistence, tests and CI that are already done properly.

## What's included

- **Authentication**: register, login, short-lived JWT access tokens, and
  refresh tokens that are persisted, rotated on every use and revoked as a
  family if an old one is replayed (reuse detection).
- **Password reset** with single-use emailed tokens, safe against account
  enumeration.
- **Rate limiting** on authentication endpoints (Bucket4j, per client IP).
- **RFC 7807 error responses** (`application/problem+json`) for every error,
  including 401, 403 and 429.
- **PostgreSQL + Flyway** migrations, `ddl-auto: validate`.
- **Integration tests on a real PostgreSQL** via Testcontainers.
- **OpenAPI / Swagger UI** (off by default in production) and **Actuator**
  health probes.
- **GitHub Actions CI** and Dependabot.

Stack: Java 17, Spring Boot 3.3, Maven, PostgreSQL 16.

## Getting started

Requirements: JDK 17 and Docker.

1. Use this repository as a GitHub template.
2. Follow [After cloning](#after-cloning) to rename the project.
3. Start local services: `docker compose up -d` (PostgreSQL, and Mailhog, a
   fake SMTP inbox at http://localhost:8025).
4. Run the app: `./mvnw spring-boot:run`
5. Open http://localhost:8080/swagger-ui.html

No Spring profile is active by default, on purpose (see
[Configuration](#configuration)). `.mvn/maven.config` activates `dev` for
Maven runs. From an IDE, set `SPRING_PROFILES_ACTIVE=dev` in the run
configuration. If startup fails complaining about a missing DataSource or
`jwt.secret`, no profile is active.

### The refresh cookie and local HTTPS

The refresh cookie is always `Secure`, in every profile, so nobody learns the
habit of turning it off "just for dev". This still works on plain
`http://localhost` in Chrome and Firefox. It does NOT work in Safari, or on
any other hostname over plain HTTP (a LAN IP, `myapp.local`); use HTTPS there
(e.g. `mkcert` plus a local reverse proxy).

A frontend on another localhost port (e.g. Vite on `:5173`) is same-site with
the API on `:8080`, so `SameSite=Strict` doesn't block the cookie. Cross-origin
calls still need `credentials: 'include'` on the frontend and its origin
listed in `app.cors.allowed-origins`.

## Project structure

```
.
├── .github/                 CI workflow and Dependabot
├── .mvn/maven.config        activates the dev profile for `spring-boot:run`
├── docker-compose.yml       local PostgreSQL + Mailhog
└── src
    ├── main
    │   ├── java/com/example/template
    │   │   ├── TemplateApplication.java
    │   │   ├── auth/        register, login, refresh, logout, password reset
    │   │   ├── user/        User entity, roles, repository
    │   │   └── common/      cross-cutting infrastructure, no business logic
    │   │       ├── config/      JPA auditing, CORS properties, OpenAPI
    │   │       ├── email/       EmailSender + SMTP and no-op implementations
    │   │       ├── exception/   DomainException hierarchy, GlobalExceptionHandler
    │   │       ├── ratelimit/   Bucket4j rules, service and filter
    │   │       └── security/    SecurityConfig, 401/403 handlers, token generator
    │   └── resources
    │       ├── application*.yml
    │       └── db/migration/    Flyway: V1 users, V2 refresh_tokens,
    │                            V3 password_reset_tokens
    └── test                 mirrors main; integration tests extend
                             AbstractIntegrationTest
```

## Architecture

**Package by feature.** Code is grouped by what it is about (`auth`, `user`),
not by technical layer. Inside a feature you find the usual controller,
service, repository, entity and `dto` pieces. When the MVP grows, a new
feature is a new package, not a new class in a giant `service` package.

**`common/` is infrastructure.** Anything cross-cutting and free of business
rules lives there. The rule: if it's tied to one domain concept, it belongs in
that feature's package. There is one deliberate exception: `SecurityConfig`
wires the `auth` feature's JWT filter into the security chain, so `common`
depends on `auth` in that single place.

**Conventions**

- **Controllers are thin.** They validate input (Bean Validation on request
  DTOs), delegate to a service and shape the response. Entities never cross
  the API boundary.
- **DTOs are Java `record`s**, mapped by hand (`toDto()` style). No mapping
  framework: every line is traceable, and for an MVP template that beats
  saving boilerplate.
- **Errors are domain exceptions.** Throw `EntityNotFoundException`,
  `ConflictException` or `BusinessRuleException` (all extend
  `DomainException`, which carries its own HTTP status and error code).
  `GlobalExceptionHandler` needs no change for new exceptions.
- **Entities** use UUID ids, JPA auditing for timestamps, a `protected`
  no-arg constructor and a static factory method, so an object can't exist
  half-built. Lombok is used for getters and constructor injection;
  `record`s, not Lombok, for DTOs.
- **Schema changes are Flyway migrations**, never Hibernate-inferred DDL.
  Hibernate only validates (`ddl-auto: validate`). Never edit a migration that
  has already been applied; add a new one.
- **Secure by default.** Every endpoint requires authentication unless it is
  explicitly opened in `SecurityConfig`. Configuration works the same way:
  safe values by default, unsafe ones need an explicit act.
- **Commits** follow Conventional Commits (`feat:`, `fix:`, `chore:`,
  `docs:`, `ci:`). The history is written to be read in order
  (`git log --reverse`): each message explains the why.

## Authentication

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/auth/register` | public | returns an access token, sets the refresh cookie |
| POST | `/api/auth/login` | public | same response as register |
| POST | `/api/auth/refresh` | refresh cookie | rotates the refresh token, returns a new access token |
| POST | `/api/auth/logout` | refresh cookie | revokes this device's token only; needs no access token |
| POST | `/api/auth/logout-all` | Bearer token | revokes every session of the user |
| POST | `/api/auth/password-reset/request` | public | always `202`, whether or not the email exists |
| POST | `/api/auth/password-reset/confirm` | public | sets a new password and revokes all sessions |

**Access token**: JWT (HS256), 15 minutes, sent as `Authorization: Bearer`.
The JWT filter reloads the user from the database on each request, so role
changes apply immediately.

**Refresh token**: a random 256-bit value, stored only as a SHA-256 hash,
delivered only in an `httpOnly`, `Secure`, `SameSite=Strict` cookie scoped to
`/api/auth`. Every use retires the token and issues a new one. Presenting an
already-rotated token is treated as theft: the user's whole token family is
revoked and everyone must log in again. A token revoked by logout (never
rotated) is simply invalid, not an alarm.

**Password reset**: the emailed token is opaque, persisted (hashed),
single-use, expires in 30 minutes, and requesting a new one invalidates the
previous. Confirming revokes all refresh tokens. The reset link points to your
frontend (`app.password-reset.reset-url-base`), which calls `/confirm`.

Roles are `USER` (default) and `ADMIN`. No endpoint creates admins; promote one
in SQL: `UPDATE users SET role = 'ADMIN' WHERE email = 'you@example.com';`

## Errors

Every error is `application/problem+json` with an `errorCode`:

| Status | `errorCode` | When |
|---|---|---|
| 400 | `validation-failed` | Bean Validation failed (see the `errors` map) |
| 400 | `invalid-password-reset-token` | reset token unknown, expired or used |
| 401 | `unauthorized` | missing or invalid access token |
| 401 | `invalid-credentials` | wrong email or password (deliberately identical) |
| 401 | `invalid-refresh-token` | refresh token missing, unknown or expired |
| 401 | `refresh-token-reuse-detected` | a rotated token was replayed |
| 403 | `forbidden` | authenticated but not allowed |
| 404 | `entity-not-found` | |
| 409 | `conflict` | e.g. email already registered |
| 422 | `business-rule-violation` | valid input that breaks a business rule |
| 429 | `rate-limit-exceeded` | see `Retry-After` |
| 500 | `internal-error` | unexpected; details are logged, never returned |

## Configuration

Profiles: `dev` (local), `test` (automatic in tests), `prod`. **No profile is
active by default.** The `dev` profile contains a fixed JWT secret that is
public in this repository; if the base config activated it, a production deploy
that forgot to set the profile would silently boot with that secret. Without a
default, forgetting the profile fails at startup instead.

In production always set the profile explicitly:
`SPRING_PROFILES_ACTIVE=prod java -jar app.jar`

| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection |
| `JWT_SECRET` | HS256 key, at least 32 characters (`openssl rand -base64 48`) |
| `CORS_ALLOWED_ORIGINS` | comma-separated frontend origins |
| `PASSWORD_RESET_URL_BASE` | frontend page that reads `?token=` |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` | outgoing email |
| `OPENAPI_ENABLED` | `true` to expose Swagger UI in prod (default off) |
| `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` | optional; see rate limiting |

`ConfigurationSafetyTest` guards the two rules above: no default profile or
secret in the base config, and prod secrets read from the environment.

## Rate limiting

Public authentication endpoints are limited per client IP with Bucket4j (token
bucket, refilled gradually). Rules live under `app.rate-limit.rules` in
`application.yml` and can be tuned without touching code:

| Group | Endpoints | Default |
|---|---|---|
| login | `/api/auth/login` | 10 per minute |
| register | `/api/auth/register` | 5 per hour |
| password-reset | `/api/auth/password-reset/**` | 5 per hour |
| session | `/api/auth/refresh`, `/api/auth/logout` | 30 per minute |

Each group has its own budget per IP. To protect a new endpoint, add a rule.

**Limitations**

- Counters are in memory, per JVM. With N instances the effective limit is N
  times higher, and a restart resets them. For a shared limit, move the
  Bucket4j state to Redis or PostgreSQL; only `RateLimitService` changes.
- Per IP only. Clients behind one shared IP (office, campus, mobile carriers)
  share a budget, and an attacker with many IPs gets one budget per IP.
  `password-reset/request` has no per-email limit, so a distributed attacker
  can still send many reset emails to one victim. IPv6 clients can rotate
  addresses inside a /64; consider keying on that prefix.

**Behind a reverse proxy.** The TCP peer is the proxy, not the user.
`server.forward-headers-strategy: native` makes Tomcat resolve the real client
IP from `X-Forwarded-For`, but only when the connection comes from a trusted
proxy; headers from anyone else are ignored, so they can't be forged to dodge
the limits. Tomcat trusts private ranges by default (10.x, 172.16-31.x,
192.168.x, loopback), which covers Docker, Kubernetes and many platforms. If
your proxy connects from a public address, list it in
`SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` (a regex). The symptom of getting
this wrong: unrelated users start receiving `429` together.

## Health and operational endpoints

Public, for your platform's probes:

- `/actuator/health/liveness`: is the process alive? Never depends on external
  services, so a database outage can't cause restart loops.
- `/actuator/health/readiness`: can it serve traffic? Includes the database.
- `/actuator/health`: aggregate.

Details are hidden (`show-details: never`; `always` only in dev). Every other
actuator endpoint (`info`, `metrics`) requires `ROLE_ADMIN`. Endpoints such as
`env`, `beans` or `heapdump` are not exposed at all; keep
`management.endpoints.web.exposure.include` an explicit list.

## Testing

```
./mvnw verify
```

You only need Docker, not `docker compose up`.

- Integration tests extend `AbstractIntegrationTest`. The whole run shares one
  PostgreSQL container (Testcontainers, started once per JVM, removed on
  exit). All tables except Flyway's history are truncated before every test,
  so tests must create their own data.
- Tests are deliberately **not** `@Transactional`: that would mask missing
  transaction boundaries in services (a bug that only shows in production).
- Rate limiting is disabled in the `test` profile because its in-memory state
  would leak across cached Spring contexts. A test that needs it re-enables it
  with `@TestPropertySource` and uses its own client addresses.
- Anything that boots the Spring context goes through
  `AbstractIntegrationTest`, so no test depends on your local database.

## Continuous integration

`.github/workflows/ci.yml` runs `./mvnw verify` on every pull request and on
pushes to `main`: the same command you run locally. Testcontainers works on
GitHub-hosted runners without extra setup. On failure, the Surefire reports
are attached to the run for 7 days. Recommended: require the `verify` check
under Settings, Branches. Repositories created from this template inherit the
workflow and Dependabot.

## After cloning

1. **Rename the package** `com.example.template` with your IDE's refactoring,
   then also replace it in the `logging.level` keys of the `application*.yml`
   files (refactoring tools usually miss YAML).
2. Update `groupId`, `artifactId` and `name` in `pom.xml`, and
   `spring.application.name`.
3. In `GlobalExceptionHandler`, replace the placeholder error type base URL
   (`https://api.example.com/errors/`).
4. Set your frontend origin (`app.cors.allowed-origins`) and
   `app.password-reset.reset-url-base` in the `dev` profile.
5. Generate a production `JWT_SECRET` and set the variables listed above in
   your platform.

## Adding a feature

Example: a `note` feature owned by the logged-in user.

1. **Package** `com.example.template.note` with `Note`, `NoteRepository`,
   `NoteService`, `NoteController` and a `dto` subpackage with request and
   response `record`s.
2. **Migration** `V4__create_notes_table.sql`:

   ```sql
   CREATE TABLE notes (
       id         UUID PRIMARY KEY,
       owner_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
       title      VARCHAR(255) NOT NULL,
       created_at TIMESTAMP WITH TIME ZONE NOT NULL,
       updated_at TIMESTAMP WITH TIME ZONE NOT NULL
   );

   CREATE INDEX idx_notes_owner_id ON notes (owner_id);
   ```

3. **Entity** following `User`: UUID id, auditing fields, `protected` no-arg
   constructor, static factory. Constraints that matter (unique, not null)
   live in the migration.
4. **Scope every query by owner.** Get the user id from the principal and
   never trust an id from the URL alone:

   ```java
   @GetMapping("/{id}")
   public NoteResponse get(@PathVariable UUID id,
                           @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails principal) {
       return noteService.get(id, principal.getId());
   }
   ```

   ```java
   Note note = noteRepository.findByIdAndOwnerId(id, ownerId)
           .orElseThrow(() -> EntityNotFoundException.forId("Note", id));
   ```

   Someone else's note is a `404`, not a `403`: a `403` would confirm the id
   exists.
5. **Errors**: throw domain exceptions; don't catch them in the controller.
6. **Transactions**: put `@Transactional` on service methods that write.
   Services that run `@Modifying` queries need one to work at all.
7. **Security**: you get "authenticated by default" for free. To open an
   endpoint, add it to `SecurityConfig` explicitly and mark it
   `@SecurityRequirements` in OpenAPI. If it can be abused anonymously, add a
   rate limit rule.
8. **Docs**: `@Tag` on the controller, `@Operation` where behavior isn't
   obvious, and `@Schema(example = ...)` on DTO fields.
9. **Tests**: extend `AbstractIntegrationTest`; cover the happy path, the
   `404` for another user's resource, and validation failures.

## Known gaps

Things this template does not do yet, so you can decide what your MVP needs:

- Expired and revoked refresh / password reset tokens are never deleted. Add a
  scheduled cleanup before the tables get large.
- Time is read with `Instant.now()` directly, so token expiry isn't unit
  tested. Injecting a `java.time.Clock` would fix it.
- No email verification on register and no account lockout (lockout was
  rejected on purpose: it lets an attacker lock the owner out).
- The password reset email is sent inside the database transaction. At scale,
  send it after commit.
- The `429` response is not yet part of the OpenAPI contract.
- Rate limit counters are per JVM (see above).
