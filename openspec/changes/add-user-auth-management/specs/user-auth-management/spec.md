## Purpose

Provide identity, personalization, and role-based administration for the
campus AI platform so users can securely access the application and
administrators can manage platform accounts.

## ADDED Requirements

### Requirement: User registration

The system SHALL allow an unauthenticated visitor to register with a unique
username and password plus optional grade, college, personal signature, and
avatar information.

#### Scenario: Register a normal user

- **WHEN** a visitor submits a valid unused username and password
- **THEN** the system SHALL create an enabled account with the ordinary-user
  role
- **AND** SHALL persist the submitted profile fields
- **AND** SHALL never return the stored password or password hash

#### Scenario: Reject a duplicate username

- **WHEN** a visitor submits a username that already exists
- **THEN** the system SHALL reject the registration with a clear conflict
  response
- **AND** SHALL not create or modify any account

#### Scenario: Reject invalid registration data

- **WHEN** a visitor submits an empty username, an unsafe password, or a field
  exceeding the documented length limit
- **THEN** the system SHALL reject the request with validation details
- **AND** SHALL not create an account

### Requirement: User login and session identity

The system SHALL authenticate users by username and password and issue a
short-lived signed access token that identifies the user and role.

#### Scenario: Login succeeds

- **WHEN** a registered user submits the correct username and password
- **THEN** the system SHALL return an access token and safe current-user
  profile data
- **AND** subsequent protected requests carrying the token SHALL be associated
  with that user

#### Scenario: Login fails

- **WHEN** a user submits an unknown username or incorrect password
- **THEN** the system SHALL return an unauthorized response
- **AND** SHALL not reveal whether the username or password was the part that
  was incorrect

#### Scenario: Disabled account cannot login

- **WHEN** a disabled account submits otherwise valid credentials
- **THEN** the system SHALL reject the login as unauthorized

### Requirement: Authenticated application access

The system SHALL require a valid authenticated identity before allowing access
to platform AI pages and their protected backend operations.

#### Scenario: Unauthenticated visitor opens a protected page

- **WHEN** a visitor without a valid access token navigates to an AI application
  route
- **THEN** the frontend SHALL redirect the visitor to the login page

#### Scenario: Expired token calls a protected API

- **WHEN** a request carries an expired, malformed, or revoked/invalid access
  token
- **THEN** the backend SHALL return unauthorized
- **AND** SHALL not invoke the AI application operation

### Requirement: Self-service profile management

The system SHALL allow an authenticated user to view and modify their own
editable profile fields, including grade, college, personal signature, and
avatar.

#### Scenario: User updates personal information

- **WHEN** an authenticated user submits valid changes to their own profile
- **THEN** the system SHALL persist the changes
- **AND** SHALL return the updated safe profile data

#### Scenario: User cannot self-edit protected account fields

- **WHEN** an ordinary user submits a request attempting to change their own
  username, role, enabled state, or password through the profile update
  operation
- **THEN** the system SHALL reject or ignore those protected fields
- **AND** SHALL preserve the server-controlled values

### Requirement: Administrator account bootstrap

The system SHALL ensure that an administrator account exists for username
the configured bootstrap username, using the configured bootstrap password on first
initialization when the account does not already exist.

#### Scenario: Bootstrap administrator on an empty installation

- **WHEN** the application starts with no account for `xunyu`
- **THEN** the system SHALL create an enabled administrator account for
  `xunyu`
- **AND** SHALL store only a one-way password hash

#### Scenario: Preserve an existing administrator account

- **WHEN** the application starts and an account for `xunyu` already exists
- **THEN** the system SHALL not reset its password or overwrite its profile
  fields automatically

### Requirement: Administrator user management

The system SHALL provide administrator-only operations for listing users,
searching/filtering users, editing ordinary-user profile data, changing user
roles, and enabling or disabling accounts.

#### Scenario: Administrator opens user management

- **WHEN** an authenticated administrator opens the user management page
- **THEN** the frontend SHALL display a user list with username, role, enabled
  state, grade, college, signature, avatar, and relevant timestamps

#### Scenario: Administrator changes a user role

- **WHEN** an administrator changes an ordinary user's role and confirms the
  change
- **THEN** the backend SHALL persist the new role
- **AND** future authorization checks SHALL use the updated role

#### Scenario: Ordinary user attempts an admin operation

- **WHEN** an ordinary user calls an administrator-only endpoint or route
- **THEN** the backend SHALL return forbidden
- **AND** the frontend SHALL not expose the administrator navigation

#### Scenario: Administrator protects the last administrator

- **WHEN** an administrator attempts to disable or demote the last enabled
  administrator account
- **THEN** the system SHALL reject the operation with a conflict
- **AND** SHALL preserve at least one enabled administrator

### Requirement: Avatar upload and display

The system SHALL accept a validated image avatar upload, persist a stable
avatar reference, and expose that reference in current-user and user-management
responses.

#### Scenario: Upload avatar with Tencent COS configured

- **WHEN** an authenticated user uploads a supported image and Tencent COS
  configuration is present
- **THEN** the system SHALL store the object in the configured COS bucket
- **AND** SHALL persist the resulting object URL or stable key on the user's
  profile

#### Scenario: Upload avatar without Tencent COS configured

- **WHEN** an authenticated user uploads a supported image and COS is not
  configured
- **THEN** the system SHALL use the local development storage fallback
- **AND** SHALL return an avatar reference that the frontend can display

#### Scenario: Reject unsafe avatar upload

- **WHEN** an upload is not an allowed image type, exceeds the configured size
  limit, or fails content validation
- **THEN** the system SHALL reject the upload
- **AND** SHALL preserve the previous avatar reference

### Requirement: Authenticated identity in the global frontend shell

The system SHALL show the current user's avatar and username in the upper-right
area of authenticated pages and provide logout access.

#### Scenario: Render the signed-in user

- **WHEN** an authenticated page loads with a valid session
- **THEN** the global shell SHALL display the user's avatar and username
- **AND** SHALL show an administrator entry point only when the user has the
  administrator role

#### Scenario: Logout

- **WHEN** the user selects logout
- **THEN** the frontend SHALL remove the locally stored session
- **AND** SHALL navigate to the login page
- **AND** subsequent protected requests SHALL not send the old access token
