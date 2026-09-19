## 1. Backend Foundation

- [x] 1.1 Add Spring Security, Spring Data JPA, validation, JWT, H2, and optional Tencent COS dependencies; verify `./mvnw -DskipTests compile` succeeds and no cloud secrets are committed.
- [x] 1.2 Add configurable datasource, JWT, bootstrap-admin, upload, and COS properties for local and production profiles; verify the application starts with local defaults and missing COS credentials.
- [x] 1.3 Create the user entity, role enum, repository, timestamps, unique username constraint, and safe DTOs for registration, login, profile, and admin updates; verify repository tests cover duplicate usernames and password hash exclusion.

## 2. Authentication And Authorization

- [x] 2.1 Implement BCrypt password encoding, registration validation, duplicate handling, and idempotent administrator bootstrap from configured credentials; verify focused service tests cover normal-user defaults, first startup, and restart preservation.
- [x] 2.2 Implement login and current-user endpoints that issue and validate signed bearer tokens without exposing password data; verify MockMvc tests cover success, wrong credentials, disabled accounts, malformed tokens, and unauthorized access.
- [x] 2.3 Add stateless security configuration and role checks for `/auth`, `/admin/users`, `/ai`, and public health/static resources; verify ordinary users receive `403` for admin APIs and unauthenticated AI calls receive `401`.

## 3. Profile And Avatar Services

- [x] 3.1 Implement authenticated self-profile read/update endpoints that only allow editable grade, college, signature, and avatar fields; verify tests prove users cannot change username, role, enabled state, or another user's data.
- [x] 3.2 Implement provider-based avatar storage with Tencent COS when fully configured and local `tmp/` fallback otherwise; verify tests cover supported images, size/type rejection, stable returned references, and preservation of the previous avatar after failure.
- [x] 3.3 Implement administrator user list/search/update endpoints for profile fields, role, and enabled state, including last-enabled-admin protection; verify MockMvc tests cover admin success, ordinary-user denial, and last-admin conflict.

## 4. Frontend Authentication Experience

- [x] 4.1 Add typed auth API methods, local auth state, bearer-token injection for Axios and SSE fetch requests, and `401` session clearing; verify TypeScript compilation and a manual login/logout request trace.
- [x] 4.2 Add login and registration views with username, password, grade, college, signature, and avatar support; verify `npm run build` succeeds and validation/error states render for duplicate or invalid submissions.
- [x] 4.3 Add router guards and an authenticated global shell with upper-right avatar, username, logout, profile link, and admin-only navigation; verify unauthenticated routes redirect to login and ordinary users cannot reach the admin route.
- [x] 4.4 Add profile view/edit and administrator user-management views with avatar display, role controls, enabled-state controls, search/filter, and conflict/error feedback; verify `npm run build` succeeds and admin controls are hidden for ordinary users.

## 5. Integration Verification

- [x] 5.1 Protect existing LoveApp and LiuManus frontend API calls with the authenticated token while preserving their request and SSE response contracts; verify authenticated smoke calls reach the existing endpoints without changing AI behavior.
- [x] 5.2 Run focused auth/profile/admin backend tests and `cd liu-ai-agent-frontend && npm run build`; document any external DashScope/MCP tests skipped because credentials or quotas are unavailable.
- [x] 5.3 Perform an end-to-end manual smoke test for bootstrap admin login, ordinary-user registration, profile edits, avatar upload fallback, admin role update, logout, and rejected ordinary-user admin access; record the tested local URLs and expected outcomes.
