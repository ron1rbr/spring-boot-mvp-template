# spring-boot-mvp-template

Reusable Spring Boot template for bootstrapping new MVPs quickly: JWT auth
with refresh token rotation, password reset, PostgreSQL + Testcontainers,
RFC 7807 error handling, OpenAPI docs and health probes.

## Getting started

1. Use this repository as a GitHub template.
2. Find and replace `com.example.template` with your own package (and
the `artifactId` in `pom.xml`).
3. `docker compose up -d` starts PostgreSQL and Mailhog, a fake SMTP inbox
at http://localhost:8025.

## Running locally

1. Start PostgreSQL: `docker compose up -d`
2. Run the app with the `dev` profile: `./mvnw spring-boot:run`

## Running tests

Integration tests extend `AbstractIntegrationTest`. The whole test run shares
one disposable PostgreSQL container (Testcontainers), started when the first
test class loads and removed when the JVM exits. All tables except Flyway's
history are truncated before every test, so tests must create their own data
and must not depend on data left by other tests.

You do NOT need `docker compose up` running; just Docker: `./mvnw verify`.

## Local development and the Secure refresh cookie

`RefreshTokenCookieFactory` sets `Secure` on the refresh cookie in every
profile, on purpose: a template shouldn't teach turning it off "just for
dev", because that flag tends to survive into staging.

This still works on plain `http://localhost`:

- Chrome and Firefox treat `localhost` as a secure context and accept
  `Secure` cookies from it. Swagger UI (`/swagger-ui.html`) is same-origin
  with the API, so login -> refresh works there.
- A frontend on another localhost port (e.g. Vite on `:5173`) is
  *same-site* with the API on `:8080`, since SameSite ignores ports. This
  means `SameSite=Strict` doesn't block the cookie. Cross-origin calls
  still need `credentials: 'include'` on the frontend and its origin listed
  in `app.cors.allowed-origins`.

Where it does NOT work, and you need HTTPS (e.g. `mkcert` plus a local
reverse proxy):

- Safari, which doesn't make that exception for `localhost`.
- Any other hostname over plain HTTP: a LAN IP for testing on a phone,
  a custom `myapp.local`, a dev container exposed under another name.

## Health checks and operational endpoints

Point your platform at these (no credentials needed):

- `/actuator/health/liveness`: is the process alive? Never depends on
  external services, so a database outage can't cause restart loops.
- `/actuator/health/readiness`: can it serve traffic? Includes the database.
- `/actuator/health`: aggregate, for platforms that allow a single path.

Details are hidden unless `show-details` is changed (`always` only in dev).
Every other actuator endpoint (`info`, `metrics`) requires `ROLE_ADMIN`.
Nothing creates admins through the API; promote one in SQL:
`UPDATE users SET role = 'ADMIN' WHERE email = 'you@example.com';`

Endpoints like `env`, `beans` or `heapdump` are not exposed at all; keep
`management.endpoints.web.exposure.include` an explicit list.

## Continuous integration

`.github/workflows/ci.yml` runs `./mvnw verify` on every pull request and on
pushes to `main`: the same command you run locally. Integration tests start
their own PostgreSQL via Testcontainers, which works on GitHub-hosted runners
without extra setup. When a run fails, the Surefire reports are attached to
it for 7 days.

Recommended: under Settings -> Branches, require the `verify` check before
merging into `main`. Repositories created from this template inherit the
workflow and the Dependabot configuration.

