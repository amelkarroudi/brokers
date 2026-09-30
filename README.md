# Brokers

A multi-tenant platform where car rental companies manage their fleet **once** and have it published and
kept in sync on **Booking.com** and **Rentalcars.com** automatically: listings, prices, photos and
availability go out through each channel's API, and reservations come back through webhooks and block
the car on every other channel.

```
                    ┌───────────────────────── api (Spring Boot) ─────────────────────────┐
  web (Next.js) ──► │  auth · locations · fleet · availability · reservations · dashboard │
                    │                         │ transactional outbox                      │
                    │                         ▼                                           │
                    │                   sync_jobs ──► SyncWorker ──► ChannelClient ───────┼──► Booking.com API
                    │                                                     ▲               ├──► Rentalcars.com API
                    │  /api/webhooks/{channel}/{connection} ──► verify ──► reservations   │
                    └─────────────────────────────────────────────────────┼───────────────┘
                                          channel-booking · channel-rentalcars (+ channel-core)
```

## Modules

| Module               | What it is                                                                                              |
|----------------------|---------------------------------------------------------------------------------------------------------|
| `channel-core`       | Channel-agnostic contract: `ChannelClient`, `ChannelWebhookHandler`, models, classified `ChannelException`. |
| `channel-booking`    | Booking.com supplier integration: HTTP Basic + supplier header, snake_case JSON, `sha256=` HMAC webhooks. |
| `channel-rentalcars` | Rentalcars.com supplier integration: OAuth2 client credentials with token cache, prices in minor units, timestamped HMAC webhooks with replay protection. |
| `api`                | Spring Boot 4 application that uses both packages.                                                       |
| `web`                | Next.js 16 + shadcn/ui dashboard to drive and test the API.                                              |

Adding a channel means writing one more package that implements the two `channel-core` interfaces and
registering it in `ChannelConfig`.

## Running locally

Requirements: Java 21, Node 22.

```bash
# API on http://localhost:8080 (H2 file database, built-in channel sandbox, background sync worker)
./gradlew :api:bootRun

# Web on http://localhost:3000 (proxies /api to the API)
cd web && npm install && npm run dev
```

Then sign up, connect both channels (any credentials work in the sandbox; use the secret `invalid` to see
a rejection), add a location and a car with a photo, and publish it. The **Test reservation** tab on the
car sends a signed webhook exactly like the channel would; the **Sandbox** page shows what each fake
channel now holds, in its own format.

Swagger UI: http://localhost:8080/swagger-ui.html

### With PostgreSQL

```bash
docker compose up -d
SPRING_PROFILES_ACTIVE=postgres ./gradlew :api:bootRun
```

## Tests

```bash
./gradlew build                  # all Java modules, API on H2
cd web && npm run typecheck && npm run build
```

To run the API suite on PostgreSQL, set `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and
`SPRING_DATASOURCE_PASSWORD`. CI does both.

- **Channel packages**: every call is tested against WireMock (payload mapping, auth, error
  classification, `Retry-After`, token caching and refresh, webhook signatures, replays, malformed bodies).
- **API**: full application tests with both channels replaced by WireMock and the sync worker driven
  explicitly, covering auth, tenant isolation, connections, sync behaviour, webhooks and availability.

## How it works

### Sync (platform → channels)

Every change to a published car writes a **sync job in the same transaction** (transactional outbox),
so a change is never saved without its propagation, or the other way round. A worker drains the queue:

- **State based, not payload based.** A job reads the current state when it runs and compares
  fingerprints (SHA-256 of what was last pushed) for content, photos and availability. Late, duplicate
  or out-of-order jobs are harmless, and unchanged data is never re-sent.
- **Coalescing.** A burst of edits merges into the single pending job for that car, channel and action.
- **Failures are classified.** Network errors, 5xx and 429 are retried with exponential backoff and
  jitter, never sooner than the channel's `Retry-After`. Validation errors fail immediately and mark the
  listing `FAILED`. A 401/403 marks the connection `INVALID_CREDENTIALS` and pauses that channel until
  the credentials are verified again, which then triggers a full resync.
- **Self-healing.** A listing deleted on the channel's side (404 on update) is recreated with all its
  photos and availability. The external id is stored immediately after creation, so a crash never
  creates a duplicate listing.
- **Safe with several workers.** Jobs are claimed with an atomic `PENDING → RUNNING` update, the worker
  skips a car that already has a job running on the same channel, and jobs abandoned by a crashed
  worker are requeued.
- No database transaction stays open while a channel is being called.

### Webhooks (channels → platform)

`POST /api/webhooks/{channel}/{connectionId}` is public but every request is authenticated by its HMAC
signature over the raw bytes:

1. Unknown or disconnected endpoint → 404. Bad or stale signature → 401. Body over 64 KB → 413.
   Malformed payload → 400.
2. The event is **stored before it is applied**; `(connection, event id)` is unique, so channel retries
   are acknowledged without being applied twice.
3. Applying it: a reservation creates or moves an availability block and queues an availability push
   to every channel. Events older than the last one applied to that reservation are ignored
   (out-of-order delivery). Cancelling an unknown reservation is ignored.
4. If the car was sold twice before the stop-sale propagated, both reservations are kept (the channels
   already confirmed them) and flagged as **overbooked**; the flag clears when one is cancelled.
5. The channel always gets a 200 once the event is stored, even if applying it failed, and failed
   events can be replayed from the UI.

### Security

- One email/password login per organization, BCrypt (cost 12). Failures take the same time whether or
  not the email exists and return the same message. The account locks for 15 minutes after 5 failures.
- Opaque bearer tokens (`brk_…`, 256 bits). Only their SHA-256 is stored; logout revokes them.
- Channel API keys, secrets and webhook secrets are encrypted at rest with AES-256-GCM and never
  returned by the API (only the last 4 characters of the key).
- Every query is scoped by organization; another tenant's resources return 404.
- A channel account can be connected to only one organization.

## Database schema

`api/src/main/resources/db/migration` (Flyway), validated against the JPA mapping at startup.

| Table                  | Purpose                                                                                  |
|------------------------|------------------------------------------------------------------------------------------|
| `organizations`        | Tenants.                                                                                 |
| `organization_members` | The login (one per organization, enforced by a unique constraint that can be dropped for teams). |
| `auth_sessions`        | Hashed bearer tokens with expiry and revocation.                                         |
| `channel_connections`  | Encrypted credentials and status per organization and channel.                           |
| `locations`            | Pick-up branches.                                                                        |
| `vehicles`, `vehicle_photos` | The fleet, the single source of truth.                                              |
| `channel_listings`     | One row per car per channel: external id, status, fingerprints of what was pushed.       |
| `reservations`         | Mirrored channel bookings, with conflict flag and last applied event time.               |
| `availability_blocks`  | Reservation, maintenance and manual blocks (half-open periods).                           |
| `webhook_events`       | Inbox of every authentic delivery; idempotency key.                                      |
| `sync_jobs`            | Outbox of pushes with attempts, backoff and errors.                                      |

Every table has UUID keys and `created_at`/`updated_at`/`version` columns where they apply, and enums
are enforced with CHECK constraints. Foreign keys, per-tenant uniqueness and indexes follow the
query paths.

## API overview

All endpoints except auth, webhooks and docs need `Authorization: Bearer <token>`. Errors are
[RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details with a stable `code`.

| Method & path                                     | Description                                              |
|---------------------------------------------------|----------------------------------------------------------|
| `POST /api/auth/signup` · `login` · `logout`, `GET /api/auth/me` | Organization login.                     |
| `GET /api/connections`                            | Both channels and their status.                          |
| `PUT /api/connections/{channel}`                  | Connect or update credentials (checked with the channel). |
| `POST /api/connections/{channel}/verify`          | Re-check credentials; resumes a paused channel.          |
| `POST /api/connections/{channel}/webhook-secret`  | Rotate the webhook secret.                               |
| `DELETE /api/connections/{channel}`               | Disconnect and take every listing off that channel.      |
| `GET/POST/PUT/DELETE /api/locations`              | Branches.                                                |
| `GET/POST/PUT/DELETE /api/vehicles`               | Fleet (`DELETE` archives).                               |
| `POST /api/vehicles/{id}/publish` · `unpublish` · `sync` | Put on sale, take off sale, force a full resync. |
| `POST /api/vehicles/{id}/photos`, `PUT …/photos/order`, `DELETE …/photos/{photoId}` | Photos.       |
| `GET/POST/DELETE /api/vehicles/{id}/availability` | Blocked periods.                                         |
| `GET /api/reservations`                           | Reservations (`conflictOnly=true` for overbookings).     |
| `GET /api/sync-jobs`, `POST /api/sync-jobs/{id}/retry` | Sync log.                                           |
| `GET /api/webhook-events`, `POST /api/webhook-events/{id}/replay` | Webhook inbox.                           |
| `GET /api/dashboard`                              | Counters for the overview page.                          |
| `POST /api/webhooks/{channel}/{connectionId}`     | Inbound channel webhooks (signature authenticated).      |
| `/api/sandbox/**`, `/sandbox/**`                  | Development tools and fake channels (sandbox only).      |

## Configuration

| Variable                        | Default                                   | Notes                                    |
|---------------------------------|-------------------------------------------|------------------------------------------|
| `BROKERS_CREDENTIAL_KEY`        | development key                           | **Required in production.** Base64 32-byte AES key, e.g. `openssl rand -base64 32`. |
| `BOOKING_API_URL`               | `http://localhost:8080/sandbox/booking`   | Booking.com supplier API base URL.       |
| `RENTALCARS_API_URL`            | `http://localhost:8080/sandbox/rentalcars`| Rentalcars.com supplier API base URL.    |
| `BROKERS_SANDBOX_ENABLED`       | `true`                                    | Set `false` in production.               |
| `BROKERS_PUBLIC_BASE_URL`       | `http://localhost:8080`                   | Used to build the webhook URLs shown to users. |
| `BROKERS_SYNC_WORKER_ENABLED`   | `true`                                    | Run the background sync worker.          |
| `BROKERS_CORS_ORIGINS`          | `http://localhost:3000`                   | Origins allowed to call the API directly. |
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | H2 file database   | Use with the `postgres` profile.         |
| `BROKERS_API_URL` (web)         | `http://localhost:8080`                   | Where the Next.js server proxies `/api`. |

## Going live with the real channels

Booking.com and Rentalcars.com only give their supplier API specifications to approved partners.
The integration packages follow the usual shape of those APIs (authentication scheme, resources,
payload conventions, webhook signing), and each channel's wire format lives in one place:

- `channel-booking/…/dto` + `BookingMapper` + `BookingClient` (paths) + `webhook/BookingWebhookPayload`
- `channel-rentalcars/…/dto` + `RentalcarsMapper` + `RentalcarsClient` (paths) + `webhook/RcWebhookPayload`

Once you have partner access, adjust those files to the official specs, point `BOOKING_API_URL` and
`RENTALCARS_API_URL` at the real endpoints, and update the WireMock expectations in the package tests.
Nothing outside the two packages needs to change.
