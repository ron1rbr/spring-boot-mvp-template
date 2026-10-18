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
