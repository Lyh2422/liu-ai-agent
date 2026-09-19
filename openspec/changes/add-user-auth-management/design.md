## Context

The backend is a Spring Boot 3.4.8 application with no existing persistence,
security, or user domain. The frontend is a Vue 3/Vite SPA with a small route
table, a shared Axios client, and no global authenticated layout. Existing AI
endpoints live below `/api/ai`, while the server context path is `/api`.

The feature crosses backend domain, security, persistence, file storage, API,
and frontend routing/layout boundaries. See `proposal.md` and the delta spec
for the desired behavior.

## Goals / Non-Goals

**Goals:**

- Add durable local user accounts with a migration path to a production
  relational database.
- Enforce authentication and role checks at the backend boundary, with matching
  frontend route guards and navigation.
- Keep passwords one-way hashed and avoid leaking secrets in API responses.
- Make Tencent COS optional so local development works without cloud
  credentials.
- Keep the existing LoveApp and LiuManus code paths focused on AI behavior.

**Non-Goals:**

- OAuth/OIDC, email verification, password reset, MFA, refresh-token rotation,
  or account recovery.
- Fine-grained permissions beyond ordinary user and administrator roles.
- Reworking the existing AI prompts, chat memory, RAG, MCP, or tool execution.

## Decisions

### 1. Use Spring Security with signed bearer tokens

Add Spring Security and a stateless signed JWT filter. Passwords are encoded
with BCrypt; controllers receive the authenticated principal from the security
context rather than trusting a user ID supplied by the browser. The frontend
stores the access token in a small auth store and sends it as a Bearer token
through the shared Axios/fetch helpers.

JWT is preferred over server-side sessions because the existing Vue app and
backend are independently served and SSE requests already use `fetch`. A
server-side session would require cookie/CORS and shared-session handling that
does not exist today. The token should have a configurable secret and expiry,
with development defaults documented but production secrets supplied through
environment variables.

### 2. Use a relational user model with Spring Data JPA

Add a `User` entity/table with a unique username, BCrypt password hash, role,
enabled state, profile fields, avatar reference, and created/updated timestamps.
Use Spring Data JPA repositories and Bean Validation on request DTOs.

The local profile should default to a file-backed H2 database so the project can
run without another service. Keep the datasource configurable so deployment can
switch to MySQL/PostgreSQL without changing the user service contract. Schema
creation should be controlled by the active profile; production should use an
explicit migration strategy before being deployed with a shared database.

### 3. Bootstrap the administrator idempotently

Implement startup seeding through an application runner/service that looks up
`xunyu` before creating it. The seed username, password, and profile defaults
are configurable environment-backed properties with the requested values as
development defaults. Existing accounts are never overwritten, which prevents a
restart from silently resetting an administrator's password.

The administrator role is an enum rather than a free-form string. Admin
operations must check the number of enabled administrators before demoting or
disabling the last one.

### 4. Separate safe response DTOs from write requests

Use separate request/response types:

- registration request: username, password, grade, college, signature, and
  optional avatar upload/reference;
- login request: username and password;
- current-user/profile response: safe identity and profile fields only;
- self-update request: editable profile fields only;
- admin update request: profile fields, role, and enabled state.

Never serialize the entity directly. Password hashes, token signing material,
and internal storage keys that are not needed by the client remain server-side.

### 5. Make avatar storage provider-based

Define an avatar storage service with a Tencent COS implementation and a local
filesystem implementation. Select COS when its bucket, region, secret ID, and
secret key are configured; otherwise use a controlled directory under `tmp/`.
Validate MIME type, extension, content signature where practical, and maximum
size before storage. Store a stable public URL or application-served reference,
not the uploaded bytes in the user table.

The COS SDK credentials must come from environment/configuration and must never
be committed to YAML. Local fallback is deliberate for development and tests;
production can fail fast or disable uploads if policy requires COS-only storage.

### 6. Add explicit API and frontend boundaries

Expose a focused `/auth` API for registration, login, current-user lookup,
profile update, and avatar upload. Expose `/admin/users` for administrator-only
list and update operations. Keep AI routes under `/ai` but require the bearer
token for them.

Add login/register routes, a global authenticated shell, profile view/edit,
admin user-management view, and router guards. The shell loads current-user
data once after login and renders the avatar, username, logout action, and an
admin link only for administrators. API failures for `401` clear the local
session and redirect to login; `403` remains a permission error.

## Risks / Trade-offs

- [Risk] A JWT cannot be instantly revoked without server-side state. ->
  Mitigation: short configurable expiry, reject disabled users during request
  authentication, and clear tokens on logout; add refresh/revocation later if
  needed.
- [Risk] The requested bootstrap password is weak and known. -> Mitigation:
  use it only as the development bootstrap default, document environment
  overrides, and require an administrator password change before production
  deployment.
- [Risk] Local avatar files are not suitable for multi-instance deployment. ->
  Mitigation: prefer COS when configured and keep the storage interface
  provider-based.
- [Risk] Adding JPA/H2 increases startup and dependency footprint. ->
  Mitigation: keep repositories and auth services isolated, use focused tests,
  and allow an external relational datasource in deployment profiles.
- [Risk] Existing frontend SSE calls bypass Axios interceptors. -> Mitigation:
  centralize bearer-header construction for both Axios calls and SSE `fetch`
  calls before protecting AI endpoints.
- [Risk] Existing integration tests call external AI services and may exhaust
  quotas. -> Mitigation: add deterministic auth/profile tests that do not call
  DashScope; keep external integration tests separately documented.

## Migration Plan

1. Add dependencies and configuration without changing existing AI behavior.
2. Create the user schema/repository, seed `xunyu`, then add auth endpoints and
   security filters.
3. Add frontend auth state, login/register/profile/admin routes and global shell.
4. Add bearer authentication to AI API calls and verify ordinary/admin access.
5. Run focused backend tests, frontend build, and manual login/admin smoke tests.
6. Roll back by disabling the new routes/filter and removing the new frontend
   routes; retain the database tables so a later redeploy does not lose users.

## Open Questions

- Production database choice and migration tooling can be selected when the
  deployment environment is known; the service contract does not depend on the
  choice.
- Whether production should enforce COS-only avatar storage can be decided by
  deployment configuration after the local fallback is verified.
