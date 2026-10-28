# Dosey Companion local protocol (v1)

This document is the implementation contract for the Android host and future desktop client.

## Transport

- Android advertises the _dosey._tcp service through Android NSD only while the user has explicitly enabled Companion.
- The listener must use TLS; plain HTTP is forbidden because medication records are sensitive.
- The desktop client pins the host certificate during an explicit pairing flow. Never accept a certificate silently.
- The service must bind to the local network interface and be reachable only from the local network.
- Discovery broadcasts only a random instance identifier and port, never user, medication, or device-owner names.

## Current bootstrap implementation

- `GET /v1/status` is available over TLS and returns protocol version, a generic service label, pairing state, `medicationDataAvailable: false`, the server time, and `certificateSha256` (uppercase SHA-256 hex bytes separated by colons).
- The Android host generates a persistent self-signed RSA certificate in Android Keystore and advertises `_dosey._tcp.` only while Companion is enabled. Android displays the certificate SHA-256 fingerprint while Companion is running.
- `POST /v1/pair/request` accepts a small JSON body containing `clientName`, creates a single short-lived (120-second) request, and returns a request ID plus a six-digit comparison code.
- Android shows the requesting device and comparison code with explicit Approve/Reject actions. The PC can poll `GET /v1/pair/status?id=<requestId>` for pending/approved/rejected/expired state.
- Pair requests are in-memory only and are cleared when Companion stops. A second simultaneous request is refused. Pair requests also require a non-empty client name of at most 80 characters, reject malformed or unexpected JSON fields, allow at most five accepted requests per minute, and enforce a minimum five-second interval between accepted requests.
- After Android approval, the PC confirms using `POST /v1/pair/confirm` with exactly `requestId` and `verificationCode`. Only a matching, still-valid approved request receives a one-time credential response. An approval action on an expired request is recorded as expired. The PC must pin the TLS certificate before starting this flow; the six-digit code alone is not sufficient.

## Pairing

Implemented request/approval foundation:
1. User enables Companion on Android.
2. The PC requests pairing over TLS with a display name.
3. Android shows the short-lived request and six-digit comparison code.
4. The user checks that the code matches the PC and explicitly approves or rejects.
5. The PC polls the request status. Requests expire after 120 seconds and are cleared when the service stops.

Before release:
- The PC must compute the SHA-256 fingerprint from the certificate presented during the TLS handshake, display it, and let the user compare it with Android. The PC must pin that exact certificate before treating the connection as trusted; the fingerprint returned inside `/v1/status` is informational and must not itself be trusted before certificate pinning.
- Validate pairing, credential issuance, snapshot transfer, writes, and revocation end-to-end with the actual desktop client. The Android host cannot independently verify that a remote client has correctly pinned the certificate.
- Per-client revocation is available in Android's Paired computers list. Authenticated requests are bounded by server concurrency limits; add/verify per-client rate limiting as part of end-to-end hardening.

## API shape

- GET /v1/status: protocol version, generic service label, pairing state, TLS certificate fingerprint, and server time. No medical details.
- POST /v1/pair/request: starts a pairing request.
- POST /v1/pair/confirm: confirms the approved short-lived code and returns the client credential once.
- GET /v1/snapshot: returns a consistent, versioned snapshot of medications, schedules, dose logs and one-off dose logs. Requires `Authorization: Bearer <credential>`.
- POST /v1/commands: accepts authenticated `record_dose` and `record_one_off` commands. Each body includes a UUID `commandId`; duplicate command IDs replay the original response without repeating the mutation. Android commits the command receipt and Room mutation in the same transaction before returning success.
  - `record_dose`: `commandId`, `type`, `medicationId`, `scheduledDate` (YYYY-MM-DD), `minutesAfterMidnight` (0–1439), `status` (`TAKEN` or `MISSED`).
  - `record_one_off`: `commandId`, `type`, `medicationName`, `doseDescription`, `scheduledDate` (YYYY-MM-DD), `minutesAfterMidnight` (0–1439).
- GET /v1/changes?after=<revision>: currently returns a full snapshot, with `resetRequired` indicating whether the requested revision differs from the current revision. Partial change-feed optimisation is deferred until the desktop client exists.

Successful snapshot responses and successful write-command responses include the monotonically increasing snapshot revision. Writes must include an idempotency key so retrying after a dropped response cannot record the same dose twice.

## Offline desktop behavior

The desktop client stores the last valid snapshot and the timestamp it was received. While disconnected it is strictly read-only. It must not queue write commands. A failed or partial refresh must never replace the last valid snapshot.

## Android lifecycle

- Companion is off by default and can be stopped by the user.
- When enabled, use a user-visible foreground service and a persistent notification.
- Respect Android background restrictions and local-network permission requirements for the target SDK.
- Stop discovery and close the listener immediately when Companion is disabled.
- Do not run a listener at boot without explicit user opt-in.


## Credential storage foundation

Android now has an isolated storage component for future per-PC credentials. It generates 256-bit random bearer tokens, persists only SHA-256 token verifiers, and encrypts the client registry with AES-256-GCM using a key held by Android Keystore. The API supports listing client metadata, constant-time verifier comparison, and revoking a client. Raw tokens are returned only once by the issuance method.

This component is wired to approved pairing and bearer-token authentication. Corrupt or undecryptable stored state fails closed rather than being silently replaced. The store supports revocation, and Android exposes a paired-computer list with per-client revoke controls. The desktop client must pin the certificate before pairing; that cannot be verified end-to-end until the PC client exists.


## Snapshot preparation foundation

Android now has an internal snapshot exporter that reads medications, schedules, scheduled-dose history, one-off-dose history, and a durable revision within a single Room transaction. A Room migration (v5 to v6) creates the revision metadata row and SQLite triggers increment it for every insert, update, or delete on any snapshot table, including writes performed outside the main repository. The exported JSON includes `protocolVersion`, `generatedAt`, and `revision`. The exporter is exposed through authenticated snapshot and changes routes. Authenticated writes support only scheduled dose status and one-off dose records. Certificate-pinned desktop pairing, revocation UI, and end-to-end tests still need to be verified before release.
