# API Documentation — crmai Backend

> Base URL: `http://localhost:8080`
> All dates are ISO 8601 strings. All IDs are UUID strings.

---

## Table of Contents

- [Authentication & Headers](#authentication--headers)
- [Error Format](#error-format)
- [Pagination](#pagination)
- [Auth Endpoints](#auth-endpoints)
  - [Login](#post-apiauthlogin)
  - [Logout](#post-apiauthlogout)
- [Contact Endpoints](#contact-endpoints)
  - [List / Search Contacts](#get-apicontacts)
  - [Get Contact](#get-apicontactsid)
  - [Create Contact](#post-apicontacts)
  - [Update Contact](#put-apicontactsid)
  - [Delete Contact](#delete-apicontactsid)
- [Ticket Endpoints](#ticket-endpoints)
  - [List / Filter Tickets](#get-apitickets)
  - [Get Ticket](#get-apiticketsid)
  - [Create Ticket](#post-apitickets)
  - [Update Ticket](#put-apiticketsid)
  - [Delete Ticket](#delete-apiticketsid)
- [Settings Endpoints](#settings-endpoints)
  - [Get Settings Pages](#get-apisettings)
- [Module Endpoints](#module-endpoints)
  - [Create Module](#post-apimodules)
  - [List Modules](#get-apimodules)
  - [Get Module](#get-apimodulesapiname)
  - [Edit Module](#put-apimodulesapiname)
  - [Delete Module](#delete-apimodulesapiname)
- [Field Endpoints](#field-endpoints)
  - [Add Field](#post-apimodulesapinamefields)
  - [List Fields](#get-apimodulesapinamefields)
  - [Delete Field](#delete-apimodulesapinamefieldsfieldapiname)
- [Dynamic Record Endpoints](#dynamic-record-endpoints)
  - [List Records](#get-apimodulesapinamerecords)
  - [Get Record](#get-apimodulesapinamerecordsid)
  - [Create Record](#post-apimodulesapinamerecords)
  - [Update Record](#put-apimodulesapinamerecordsid)
  - [Delete Record](#delete-apimodulesapinamerecordsid)
- [Health Check](#health-check)

---

## Authentication & Headers

Every request **except `/api/auth/login`** requires both headers:

| Header | Required | Description |
|---|---|---|
| `X-Tenant-Id` | **Always** | Subdomain of the organisation, e.g. `acme`. Identifies which tenant database to use. |
| `Authorization` | All authenticated routes | `Bearer <jwt_token>` — token received from login. |
| `Content-Type` | POST / PUT | `application/json` |

**Missing `X-Tenant-Id`** → `400 Bad Request` before any other processing.
**Unknown `X-Tenant-Id`** → `400 Bad Request`.
**Missing or invalid JWT** → `401 Unauthorized`.
**JWT tenant claim does not match header** → `403 Forbidden` (token issued for a different org).
**Logged-out / revoked token** → `401 Unauthorized` (blacklisted in Redis).

### Minimal request example (authenticated)
```
GET /api/contacts HTTP/1.1
Host: localhost:8080
X-Tenant-Id: acme
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

---

## Error Format

All error responses share this structure:

```json
{
  "status": 400,
  "message": "Human-readable description",
  "details": null,
  "timestamp": "2026-06-07T10:30:00.000"
}
```

For validation errors, `details` is a map of field names to messages:

```json
{
  "status": 400,
  "message": "Validation failed",
  "details": {
    "email": "must be a well-formed email address",
    "password": "must not be blank"
  },
  "timestamp": "2026-06-07T10:30:00.000"
}
```

### HTTP Status Codes Used

| Code | Meaning |
|---|---|
| `200 OK` | Successful GET / PUT |
| `201 Created` | Successful POST |
| `204 No Content` | Successful DELETE or logout |
| `400 Bad Request` | Missing header, unknown tenant, validation failure |
| `401 Unauthorized` | Missing, expired, or revoked JWT |
| `403 Forbidden` | JWT/tenant mismatch, account disabled, not a member of this org |
| `404 Not Found` | Resource does not exist |
| `500 Internal Server Error` | Unexpected server error |

---

## Pagination

List endpoints accept Spring Data pagination query parameters:

| Param | Type | Default | Description |
|---|---|---|---|
| `page` | integer | `0` | Zero-based page number |
| `size` | integer | `20` | Items per page |
| `sort` | string | none | Field name, optionally with `,asc` or `,desc`. Example: `createdAt,desc` |

### Page Response Shape

```json
{
  "content": [ /* array of items */ ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": { "sorted": true, "direction": "DESC", "property": "createdAt" }
  },
  "totalElements": 87,
  "totalPages": 5,
  "last": false,
  "first": true,
  "numberOfElements": 20,
  "size": 20,
  "number": 0,
  "empty": false
}
```

Use `content` for the items, `totalElements` for count badges, `totalPages` for pagination controls.

---

## Auth Endpoints

### `POST /api/auth/login`

Authenticates a user against the specified org. Returns a JWT valid for 24 hours.

**No `Authorization` header needed. `X-Tenant-Id` header is required.**

#### Request Headers

```
X-Tenant-Id: acme
Content-Type: application/json
```

#### Request Body

```json
{
  "email": "john@acme.com",
  "password": "secret123"
}
```

| Field | Type | Required | Validation |
|---|---|---|---|
| `email` | string | yes | Must be a valid email format |
| `password` | string | yes | Must not be blank |

#### Response `200 OK`

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiI4ZjI...",
  "email": "john@acme.com",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "tenantId": "acme",
  "expiresIn": 86400000
}
```

| Field | Type | Description |
|---|---|---|
| `token` | string | JWT — include as `Authorization: Bearer <token>` on all subsequent requests |
| `email` | string | The authenticated user's email |
| `userId` | string (UUID) | The user's global UUID (same across all orgs they belong to) |
| `tenantId` | string | Echo of the `X-Tenant-Id` header — confirm the org the session is scoped to |
| `expiresIn` | number | Token lifetime in milliseconds (86400000 = 24 hours) |

#### Error Responses

| Status | When |
|---|---|
| `400` | Validation failure (blank email / password) |
| `400` | Unknown `X-Tenant-Id` |
| `401` | Wrong email or password |
| `403` | Account globally disabled (`platform_users.active = false`) |
| `403` | User is not a member of this org |
| `403` | User not configured in this org's database |

---

### `POST /api/auth/logout`

Revokes the current JWT immediately across all server instances. The token is blacklisted in Redis until it would have naturally expired.

#### Request Headers

```
X-Tenant-Id: acme
Authorization: Bearer <token>
```

#### Request Body

None.

#### Response `204 No Content`

Empty body. After this call the token will return `401` on any further request.

---

## Contact Endpoints

All contact endpoints require:
```
X-Tenant-Id: acme
Authorization: Bearer <token>
```

### Contact Object

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "firstName": "Jane",
  "lastName": "Doe",
  "email": "jane@example.com",
  "phone": "+1-555-0100",
  "company": "Acme Corp",
  "jobTitle": "Product Manager",
  "description": "Met at SaaStr 2026",
  "ownerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "createdAt": "2026-06-07T08:00:00",
  "updatedAt": "2026-06-07T08:00:00",
  "createdBy": "john@acme.com",
  "updatedBy": "john@acme.com"
}
```

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | Auto-generated |
| `firstName` | string | no | Required |
| `lastName` | string | no | Required |
| `email` | string | yes | Must be unique per tenant |
| `phone` | string | yes | |
| `company` | string | yes | |
| `jobTitle` | string | yes | |
| `description` | string | yes | Free text |
| `ownerId` | UUID | yes | `User.id` of the owning agent |
| `createdAt` | datetime | no | Set by server |
| `updatedAt` | datetime | no | Set by server |
| `createdBy` | string | yes | Email of the user who created it |
| `updatedBy` | string | yes | Email of the user who last updated it |

---

### `GET /api/contacts`

Returns a paginated list of contacts. Pass `search` to filter by name, email, or company.

#### Query Parameters

| Param | Type | Required | Description |
|---|---|---|---|
| `search` | string | no | Case-insensitive substring match across firstName, lastName, email, company |
| `page` | integer | no | Default `0` |
| `size` | integer | no | Default `20` |
| `sort` | string | no | e.g. `lastName,asc` or `createdAt,desc` |

#### Examples

```
GET /api/contacts?page=0&size=10&sort=lastName,asc
GET /api/contacts?search=jane&page=0&size=20
```

#### Response `200 OK`

```json
{
  "content": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "firstName": "Jane",
      "lastName": "Doe",
      "email": "jane@example.com",
      "phone": "+1-555-0100",
      "company": "Acme Corp",
      "jobTitle": "Product Manager",
      "description": null,
      "ownerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "createdAt": "2026-06-07T08:00:00",
      "updatedAt": "2026-06-07T08:00:00",
      "createdBy": "john@acme.com",
      "updatedBy": "john@acme.com"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0,
  "first": true,
  "last": true,
  "empty": false
}
```

---

### `GET /api/contacts/{id}`

Returns a single contact by ID.

#### Path Parameter

| Param | Type | Description |
|---|---|---|
| `id` | UUID | Contact ID |

#### Response `200 OK`

Single [Contact Object](#contact-object).

#### Error Responses

| Status | When |
|---|---|
| `404` | Contact not found |

---

### `POST /api/contacts`

Creates a new contact.

#### Request Body

```json
{
  "firstName": "Jane",
  "lastName": "Doe",
  "email": "jane@example.com",
  "phone": "+1-555-0100",
  "company": "Acme Corp",
  "jobTitle": "Product Manager",
  "description": "Met at SaaStr 2026",
  "ownerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6"
}
```

| Field | Required | Notes |
|---|---|---|
| `firstName` | yes | |
| `lastName` | yes | |
| `email` | no | Must be unique within this org if provided |
| `phone` | no | |
| `company` | no | |
| `jobTitle` | no | |
| `description` | no | |
| `ownerId` | no | UUID of the owning user |

Do **not** send `id`, `createdAt`, `updatedAt`, `createdBy`, `updatedBy` — all are set by the server.

#### Response `201 Created`

The created [Contact Object](#contact-object) with server-generated `id` and audit fields.

#### Error Responses

| Status | When |
|---|---|
| `400` | Email already exists for another contact in this org |

---

### `PUT /api/contacts/{id}`

Replaces a contact's data. Send all fields you want to keep.

#### Path Parameter

| Param | Type | Description |
|---|---|---|
| `id` | UUID | Contact ID |

#### Request Body

Same shape as [POST](#post-apicontacts). Omitted optional fields are set to null.

#### Response `200 OK`

Updated [Contact Object](#contact-object).

#### Error Responses

| Status | When |
|---|---|
| `404` | Contact not found |
| `400` | Email collision with a different contact |

---

### `DELETE /api/contacts/{id}`

Permanently deletes a contact.

#### Path Parameter

| Param | Type | Description |
|---|---|---|
| `id` | UUID | Contact ID |

#### Response `204 No Content`

Empty body.

#### Error Responses

| Status | When |
|---|---|
| `404` | Contact not found |

---

## Ticket Endpoints

All ticket endpoints require:
```
X-Tenant-Id: acme
Authorization: Bearer <token>
```

### Ticket Object

```json
{
  "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "subject": "Cannot export reports",
  "description": "Clicking the Export button shows a 500 error.",
  "status": "OPEN",
  "priority": "HIGH",
  "assigneeId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "contactId": "550e8400-e29b-41d4-a716-446655440000",
  "createdAt": "2026-06-07T09:15:00",
  "updatedAt": "2026-06-07T09:15:00",
  "createdBy": "john@acme.com",
  "updatedBy": "john@acme.com"
}
```

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | Auto-generated |
| `subject` | string | no | Required |
| `description` | string | yes | Free text |
| `status` | enum | no | `OPEN` \| `IN_PROGRESS` \| `RESOLVED` \| `CLOSED`. Default: `OPEN` |
| `priority` | enum | no | `LOW` \| `MEDIUM` \| `HIGH` \| `URGENT`. Default: `MEDIUM` |
| `assigneeId` | UUID | yes | `User.id` of the assigned agent |
| `contactId` | UUID | yes | Links ticket to a contact |
| `createdAt` | datetime | no | Set by server |
| `updatedAt` | datetime | no | Set by server |
| `createdBy` | string | yes | |
| `updatedBy` | string | yes | |

---

### `GET /api/tickets`

Returns a paginated list of tickets. Pass `status` to filter.

#### Query Parameters

| Param | Type | Required | Values |
|---|---|---|---|
| `status` | enum | no | `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED` |
| `page` | integer | no | Default `0` |
| `size` | integer | no | Default `20` |
| `sort` | string | no | e.g. `createdAt,desc`, `priority,asc` |

#### Examples

```
GET /api/tickets?page=0&size=20&sort=createdAt,desc
GET /api/tickets?status=OPEN&size=10
GET /api/tickets?status=IN_PROGRESS&sort=priority,desc
```

#### Response `200 OK`

Paginated response with `content` array of [Ticket Objects](#ticket-object). Same pagination envelope as contacts.

---

### `GET /api/tickets/{id}`

Returns a single ticket by ID.

#### Path Parameter

| Param | Type | Description |
|---|---|---|
| `id` | UUID | Ticket ID |

#### Response `200 OK`

Single [Ticket Object](#ticket-object).

#### Error Responses

| Status | When |
|---|---|
| `404` | Ticket not found |

---

### `POST /api/tickets`

Creates a new ticket.

#### Request Body

```json
{
  "subject": "Cannot export reports",
  "description": "Clicking Export shows a 500 error.",
  "status": "OPEN",
  "priority": "HIGH",
  "assigneeId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "contactId": "550e8400-e29b-41d4-a716-446655440000"
}
```

| Field | Required | Notes |
|---|---|---|
| `subject` | yes | |
| `description` | no | |
| `status` | no | Defaults to `OPEN` if omitted |
| `priority` | no | Defaults to `MEDIUM` if omitted |
| `assigneeId` | no | UUID of an agent |
| `contactId` | no | UUID of a linked contact |

#### Response `201 Created`

The created [Ticket Object](#ticket-object).

---

### `PUT /api/tickets/{id}`

Updates a ticket. Typically used to change status, reassign, or update description.

#### Path Parameter

| Param | Type | Description |
|---|---|---|
| `id` | UUID | Ticket ID |

#### Request Body

Same shape as [POST](#post-apitickets). Send all fields you want to keep.

#### Common update patterns

**Resolve a ticket:**
```json
{ "subject": "Cannot export reports", "status": "RESOLVED", "priority": "HIGH" }
```

**Reassign:**
```json
{ "subject": "Cannot export reports", "status": "IN_PROGRESS", "priority": "HIGH", "assigneeId": "<new-agent-uuid>" }
```

#### Response `200 OK`

Updated [Ticket Object](#ticket-object).

#### Error Responses

| Status | When |
|---|---|
| `404` | Ticket not found |

---

### `DELETE /api/tickets/{id}`

Permanently deletes a ticket.

#### Path Parameter

| Param | Type | Description |
|---|---|---|
| `id` | UUID | Ticket ID |

#### Response `204 No Content`

Empty body.

#### Error Responses

| Status | When |
|---|---|
| `404` | Ticket not found |

---

## Settings Endpoints

The Settings API returns a static registry of all available settings pages — what pages exist, their actions, and what field types are supported. No authentication is required beyond the `X-Tenant-Id` header.

### `GET /api/settings`

Returns all available settings pages and their supported actions.

#### Response `200 OK`

```json
{
  "count": 1,
  "pages": [
    {
      "key": "modules",
      "title": "Module Manager",
      "description": "Create and configure custom modules with their fields",
      "actions": [
        { "key": "create_module", "label": "Create Module",  "method": "POST",   "endpoint": "/api/modules" },
        { "key": "edit_module",   "label": "Edit Module",    "method": "PUT",    "endpoint": "/api/modules/{apiName}" },
        { "key": "add_field",     "label": "Add Field",      "method": "POST",   "endpoint": "/api/modules/{apiName}/fields" },
        { "key": "delete_field",  "label": "Delete Field",   "method": "DELETE", "endpoint": "/api/modules/{apiName}/fields/{fieldApiName}" }
      ],
      "fieldTypes": ["TEXT", "TEXT_AREA", "PHONE_NO", "EMAIL", "DATE", "NUMBER", "CHECKBOX", "CURRENCY", "URL", "AUTO_NUMBER"]
    }
  ]
}
```

| Field | Description |
|---|---|
| `count` | Total number of settings pages currently available |
| `pages[].key` | Unique identifier for the page — use to route the UI |
| `pages[].actions` | List of backend operations the UI should expose for this page |
| `pages[].fieldTypes` | Field types the user can pick when creating fields on a module |

> Use this endpoint at app startup to dynamically build your settings navigation. Adding new setting pages in a future backend release will be reflected here without a UI code change.

---

## Module Endpoints

Custom Modules let you define new entity types (like "Properties", "Orders") with their own fields and records. System modules (Contacts, Tickets) cannot be created or deleted via this API.

All module endpoints require:
```
X-Tenant-Id: acme
Authorization: Bearer <token>
```

### Module Object

```json
{
  "id": "c2a8e4f1-1234-4abc-8def-000000000001",
  "apiName": "properties",
  "displayName": "Properties",
  "type": "CUSTOM",
  "tableName": "cm_properties",
  "active": true,
  "fields": [],
  "createdAt": "2026-06-07T10:00:00",
  "updatedAt": "2026-06-07T10:00:00"
}
```

| Field | Type | Notes |
|---|---|---|
| `apiName` | string | Unique, lowercase, letters/digits/underscores only. Used in all API paths. |
| `tableName` | string | Auto-generated as `cm_{apiName}`. Physical PostgreSQL table in the tenant DB. |
| `type` | enum | `CUSTOM` (user-created) or `SYSTEM` (built-in: Contacts, Tickets). |
| `fields` | array | Populated only on `GET /api/modules/{apiName}`. Empty list on list endpoint. |

---

### `POST /api/modules`

Creates a new custom module and its physical database table.

#### Request Body

```json
{
  "apiName": "properties",
  "displayName": "Properties"
}
```

| Field | Required | Notes |
|---|---|---|
| `apiName` | yes | Lowercase letters, digits, underscores. Must start with a letter. E.g. `deal_pipeline` |
| `displayName` | yes | Human-readable label shown in the UI |

#### Response `201 Created`

The created [Module Object](#module-object).

#### Error Responses

| Status | When |
|---|---|
| `400` | `apiName` fails pattern validation |
| `409` | A module with this `apiName` already exists |

---

### `GET /api/modules`

Returns all active modules for the tenant. Fields list is empty here — use `GET /api/modules/{apiName}` to include fields.

#### Response `200 OK`

```json
[
  {
    "id": "...",
    "apiName": "contacts",
    "displayName": "Contacts",
    "type": "SYSTEM",
    "tableName": "contacts",
    "active": true,
    "fields": []
  },
  {
    "id": "...",
    "apiName": "properties",
    "displayName": "Properties",
    "type": "CUSTOM",
    "tableName": "cm_properties",
    "active": true,
    "fields": []
  }
]
```

---

### `GET /api/modules/{apiName}`

Returns a single module with its full field definitions.

#### Path Parameter

| Param | Type | Description |
|---|---|---|
| `apiName` | string | Module API name, e.g. `properties` |

#### Response `200 OK`

[Module Object](#module-object) with `fields` array populated.

#### Error Responses

| Status | When |
|---|---|
| `404` | Module not found or inactive |

---

### `PUT /api/modules/{apiName}`

Updates the display name of an existing module. The `apiName` and physical `tableName` cannot be changed after creation.

#### Request Body

```json
{ "displayName": "Real Estate Properties" }
```

| Field | Required | Notes |
|---|---|---|
| `displayName` | yes | New human-readable label |

#### Response `200 OK`

Updated [Module Object](#module-object).

#### Error Responses

| Status | When |
|---|---|
| `400` | `displayName` is blank |
| `404` | Module not found |

---

### `DELETE /api/modules/{apiName}`

Soft-deletes the module (`active = false`). The physical database table is **not** dropped — data is preserved. System modules (`SYSTEM` type) cannot be deleted.

#### Response `204 No Content`

#### Error Responses

| Status | When |
|---|---|
| `403` | Attempted to delete a SYSTEM module |
| `404` | Module not found |

---

## Field Endpoints

Fields define the schema of a custom module. Adding a field adds a column to the physical table. Deleting a field soft-deletes the metadata — the column is **not** dropped.

### Field Object

```json
{
  "id": "f1a2b3c4-0000-0000-0000-000000000001",
  "apiName": "location",
  "displayName": "Location",
  "fieldType": "TEXT",
  "required": false,
  "active": true,
  "defaultValue": null
}
```

### Field Types

| `fieldType` | PostgreSQL column | Notes |
|---|---|---|
| `TEXT` | `VARCHAR(255)` | Short single-line text — names, labels, status values |
| `TEXT_AREA` | `TEXT` | Long multi-line text — descriptions, notes, comments |
| `PHONE_NO` | `VARCHAR(50)` | Phone number |
| `EMAIL` | `VARCHAR(255)` | Email address |
| `DATE` | `DATE` | Calendar date (no time component) |
| `NUMBER` | `NUMERIC(20,4)` | Decimal numbers — quantities, measurements |
| `CHECKBOX` | `BOOLEAN` | true / false |
| `CURRENCY` | `NUMERIC(20,2)` | Monetary value (2 decimal places) |
| `URL` | `VARCHAR(500)` | Web URL |
| `AUTO_NUMBER` | `BIGINT GENERATED ALWAYS AS IDENTITY` | System-managed counter — value set by DB |

---

### `POST /api/modules/{apiName}/fields`

Adds a new field to a module. Executes `ALTER TABLE cm_{apiName} ADD COLUMN` in the tenant DB.

#### Request Body

```json
{
  "apiName": "location",
  "displayName": "Location",
  "fieldType": "TEXT",
  "required": false,
  "defaultValue": null
}
```

| Field | Required | Notes |
|---|---|---|
| `apiName` | yes | Lowercase, letters/digits/underscores, starts with letter |
| `displayName` | yes | UI label |
| `fieldType` | yes | One of the enum values above |
| `required` | no | Defaults to `false` — metadata only, not enforced by DB NOT NULL |
| `defaultValue` | no | Stored as string; used as a UI hint |

#### Response `201 Created`

The created [Field Object](#field-object).

#### Error Responses

| Status | When |
|---|---|
| `400` | `apiName` fails validation, `fieldType` is invalid |
| `404` | Module not found |
| `409` | A field with this `apiName` already exists on this module |

---

### `GET /api/modules/{apiName}/fields`

Returns all active fields for a module.

#### Response `200 OK`

Array of [Field Objects](#field-object).

---

### `DELETE /api/modules/{apiName}/fields/{fieldApiName}`

Soft-deletes a field (`active = false`). Column is not dropped from the table.

#### Response `204 No Content`

#### Error Responses

| Status | When |
|---|---|
| `404` | Module or field not found |

---

## Dynamic Record Endpoints

Records are rows in the module's physical table. The column set is determined by the module's active field definitions. System columns (`id`, `created_at`, `updated_at`, `created_by`, `updated_by`) are managed by the server and cannot be sent in the request body.

> **Security note:** Only values for known active fields are inserted/updated. Any keys in the request body that do not match an active field `apiName` are silently ignored.

---

### `GET /api/modules/{apiName}/records`

Returns a paginated list of records from the module's table, ordered by `created_at DESC`.

#### Query Parameters

| Param | Type | Required | Default |
|---|---|---|---|
| `page` | integer | no | `0` |
| `size` | integer | no | `20` |

#### Response `200 OK`

```json
{
  "content": [
    {
      "id": "a1b2c3d4-0000-0000-0000-000000000001",
      "location": "San Francisco",
      "price": 1250000.0000,
      "active": true,
      "created_at": "2026-06-07T10:00:00",
      "updated_at": "2026-06-07T10:00:00",
      "created_by": "john@acme.com",
      "updated_by": "john@acme.com"
    }
  ],
  "totalElements": 1,
  "page": 0,
  "size": 20,
  "totalPages": 1
}
```

> Column names in records use snake_case matching the `apiName` values of the fields.

---

### `GET /api/modules/{apiName}/records/{id}`

Returns a single record by UUID.

#### Response `200 OK`

A single record object (same shape as items in `content` above).

#### Error Responses

| Status | When |
|---|---|
| `404` | Record not found |

---

### `POST /api/modules/{apiName}/records`

Creates a new record in the module's table.

#### Request Body

Send a flat JSON object with field `apiName` keys. Only active field names are used; system columns are ignored.

```json
{
  "location": "San Francisco",
  "price": 1250000,
  "bedrooms": 3,
  "listed": true
}
```

Do **not** send: `id`, `created_at`, `updated_at`, `created_by`, `updated_by`.

#### Response `201 Created`

The created record with all columns including server-generated system columns.

#### Error Responses

| Status | When |
|---|---|
| `404` | Module not found |
| `400` | No valid fields in the request body |

---

### `PUT /api/modules/{apiName}/records/{id}`

Partial update — only fields present in the request body are updated. Omitted fields are left unchanged.

#### Request Body

```json
{
  "price": 1199000,
  "listed": false
}
```

#### Response `200 OK`

The updated record with all columns.

#### Error Responses

| Status | When |
|---|---|
| `404` | Module or record not found |
| `400` | No updatable fields provided |

---

### `DELETE /api/modules/{apiName}/records/{id}`

Permanently deletes a record from the table.

#### Response `204 No Content`

#### Error Responses

| Status | When |
|---|---|
| `404` | Module or record not found |

---

## Health Check

### `GET /actuator/health`

No headers required. Returns server health status.

#### Response `200 OK`

```json
{ "status": "UP" }
```

---

## Quick Reference — All Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/auth/login` | No JWT | Login, get token |
| `POST` | `/api/auth/logout` | JWT | Revoke current token |
| `GET` | `/api/contacts` | JWT | List / search contacts (paginated) |
| `GET` | `/api/contacts/{id}` | JWT | Get single contact |
| `POST` | `/api/contacts` | JWT | Create contact |
| `PUT` | `/api/contacts/{id}` | JWT | Update contact |
| `DELETE` | `/api/contacts/{id}` | JWT | Delete contact |
| `GET` | `/api/tickets` | JWT | List / filter tickets (paginated) |
| `GET` | `/api/tickets/{id}` | JWT | Get single ticket |
| `POST` | `/api/tickets` | JWT | Create ticket |
| `PUT` | `/api/tickets/{id}` | JWT | Update ticket |
| `DELETE` | `/api/tickets/{id}` | JWT | Delete ticket |
| `GET` | `/api/settings` | JWT | Settings pages registry |
| `POST` | `/api/modules` | JWT | Create custom module |
| `GET` | `/api/modules` | JWT | List all active modules |
| `GET` | `/api/modules/{apiName}` | JWT | Get module with fields |
| `PUT` | `/api/modules/{apiName}` | JWT | Edit module display name |
| `DELETE` | `/api/modules/{apiName}` | JWT | Soft-delete module |
| `POST` | `/api/modules/{apiName}/fields` | JWT | Add field to module |
| `GET` | `/api/modules/{apiName}/fields` | JWT | List active fields |
| `DELETE` | `/api/modules/{apiName}/fields/{fieldApiName}` | JWT | Soft-delete field |
| `GET` | `/api/modules/{apiName}/records` | JWT | List records (paginated) |
| `GET` | `/api/modules/{apiName}/records/{id}` | JWT | Get single record |
| `POST` | `/api/modules/{apiName}/records` | JWT | Create record |
| `PUT` | `/api/modules/{apiName}/records/{id}` | JWT | Update record (partial) |
| `DELETE` | `/api/modules/{apiName}/records/{id}` | JWT | Delete record |
| `GET` | `/actuator/health` | None | Server health |

---

## UI Integration Notes

### Storing the token
After login, store `token` and `userId` in memory or `localStorage`. Store `tenantId` separately — attach it as `X-Tenant-Id` on every request via an Axios/Fetch interceptor.

### Axios interceptor example
```js
axios.interceptors.request.use(config => {
  const token    = localStorage.getItem('token');
  const tenantId = localStorage.getItem('tenantId');
  if (token)    config.headers['Authorization'] = `Bearer ${token}`;
  if (tenantId) config.headers['X-Tenant-Id']   = tenantId;
  return config;
});
```

### Token expiry
`expiresIn` is milliseconds. Compute the expiry time as `Date.now() + expiresIn` and refresh before it expires, or redirect to login on `401`.

### Ticket status flow
```
OPEN → IN_PROGRESS → RESOLVED → CLOSED
```
Use PUT with just the new `status` (and required `subject`) to transition.

### Enum values — exact strings expected by the API

**Ticket status:** `OPEN` `IN_PROGRESS` `RESOLVED` `CLOSED`

**Ticket priority:** `LOW` `MEDIUM` `HIGH` `URGENT`
