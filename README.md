# spring-boot-mvp-template

Reusable Spring Boot template for bootstrapping new MVPs quickly: JWT auth
with refresh token rotation, password reset, PostgreSQL + Testcontainers,
RFC 7807 error handling, OpenAPI docs, health probes and rate limiting on
authentication endpoints.

## Getting started

1. Use this repository as a GitHub template.
2. Find and replace `com.example.template` with your own package (and
the `artifactId` in `pom.xml`).
3. `docker compose up -d` starts PostgreSQL and Mailhog, a fake SMTP inbox
at http://localhost:8025.

## Running locally

1. Start the local services: `docker compose up -d`
2. Run the app: `./mvnw spring-boot:run`

No Spring profile is active by default, on purpose (see below).
`.mvn/maven.config` makes the Maven run activate `dev` for you. From an IDE,
set `SPRING_PROFILES_ACTIVE=dev` in the run configuration.

If startup fails complaining about a missing DataSource or `jwt.secret`,
no profile is active.

In production, always set the profile explicitly:
`SPRING_PROFILES_ACTIVE=prod java -jar app.jar`.

### Why there is no default profile

The `dev` profile contains a fixed JWT secret that is public in this
repository. If `application.yml` activated `dev` by default, a production
deploy that forgot to set the profile would silently boot with that secret.
Without a default, forgetting the profile fails at startup instead.

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

## Rate limiting

Public authentication endpoints are rate limited per client IP with Bucket4j
(token bucket, refilled gradually). Rules live in `application.yml` under
`app.rate-limit.rules` and can be tuned without touching code:

| Group          | Endpoints                       | Default       |
|----------------|---------------------------------|---------------|
| login          | `/api/auth/login`               | 10 per minute |
| register       | `/api/auth/register`            | 5 per hour    |
| password-reset | `/api/auth/password-reset/**`   | 5 per hour    |
| session        | `/api/auth/refresh`, `/logout`  | 30 per minute |

Each group has its own budget per IP. Going over a limit returns `429` as
`application/problem+json` with a `Retry-After` header (seconds).

### Known limitations

- **Counters are in memory.** With N instances behind a load
balancer the effective limit is N times higher, and a restart resets all
counters. When you need a shared limit, move the Bucket4j state to Redis or
PostgreSQL: only `RateLimitService` changes.
- **Per IP only.** Clients behind one shared IP (office or campus NAT, mobile
carries) share a budget, and an attacker with many IPs gets one budget per
IP. In particular `password-reset/request` has no per-email limit, so a
distributed attacker can still send many reset emails to one victim.
IPv6 clients can rotate addresses inside a /64; consider keying on the
/64 prefix if that matters for you.

### Behind a reverse proxy

Behind a proxy (Railway, Render, Fly, a load balancer) the TCP peer is the
proxy, not the user. `server.forward-headers-strategy: native` makes Tomcat
resolve the real client IP from `X-Forwarded-For`, but **only when the
connection comes from a trusted proxy**; headers from anyone else are
ignored, so they cannot be forged to dodge the limits.

Tomcat trusts private ranges by default (10.x, 172.16-31.x, 192.168.x,
loopback), which covers Docker, Kubernetes and the internal network of many
platforms. If you proxy connects from a public address (a CDN in front, for
example), list it with the `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES`
anvironment variable (a regex). If this is wrong, the symptom is that
unrelated users start getting `429` together: they all look like the proxy.

