## Why

The application currently exposes AI chat pages without user identity, role
checks, or profile management, so it cannot distinguish administrators from
ordinary users or provide a personalized campus platform experience.

This change adds first-class user registration, login, profile editing, and
administrator user management across the Spring Boot backend and Vue frontend.

## What Changes

- Add username/password registration and login with token-based authentication.
- Seed an administrator account from configured bootstrap credentials; all
  self-registered users default to the ordinary user role.
- Add user profile fields: username, grade, college, personal signature, avatar,
  and role metadata.
- Add "my profile" APIs and UI so users can view and edit their own public
  profile information.
- Add an administrator-only user management UI and backend APIs to view users,
  edit ordinary user profile fields, and update user roles.
- Add a global frontend shell that shows the logged-in user's avatar and
  username in the upper-right corner.
- Add avatar upload support with Tencent COS as the preferred storage backend
  and a development fallback when COS credentials are not configured.
- Protect platform application routes and APIs that require a logged-in user.

Non-goals:

- Do not change LoveApp or LiuManus model prompts, tool behavior, or RAG logic.
- Do not implement third-party OAuth, password reset, email/SMS verification, or
  multi-tenant organization management.
- Do not require Tencent COS credentials for local development.

## Capabilities

### New Capabilities

- `user-auth-management`: User registration, login, profile editing,
  role-based access control, administrator user management, and avatar storage.

### Modified Capabilities

- None.

## Impact

- Backend modules under `src/main/java/com/lyh/liuaiagent`:
  authentication/security configuration, user domain model, persistence,
  controllers, services, DTOs, and avatar storage integration.
- Backend configuration under `src/main/resources/application*.yml` for token
  secrets, admin seed defaults, persistence, and Tencent COS settings.
- Maven dependencies for security, validation, persistence, database driver,
  password hashing, and optional COS SDK support.
- Frontend modules under `liu-ai-agent-frontend/src`: routes, API client,
  auth state, global layout, login/register/profile/admin views, and protected
  route guards.
- Tests: focused backend tests for auth/profile/admin behavior and frontend
  build validation.
