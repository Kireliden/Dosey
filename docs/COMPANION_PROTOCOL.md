# Dosey Companion local protocol (v1)

This document is the implementation contract for the Android host and future desktop client.

## Transport

- Android advertises the _dosey._tcp service through Android NSD only while the user has explicitly enabled Companion.
- The listener must use TLS; plain HTTP is forbidden because medication records are sensitive.
- The desktop client pins the host certificate during an explicit pairing flow. Never accept a certificate silently.
- The service must bind to the local network interface and be reachable only from the local network.
- Discovery broadcasts only a random instance identifier and port, never user, medication, or device-owner names.

## Pairing

1. User enables Companion on Android.
2. The PC discovers the service and requests pairing over TLS.
3. Android displays a short-lived pairing code and details of the requesting client.
4. The user confirms the code and approves the request on Android.
5. Android issues a random per-client bearer credential, returned only over the pinned TLS connection.
6. Android stores only a verifier for the credential, supports revocation, and rate-limits failed attempts.
7. Subsequent sessions require the credential and pinned certificate.

## API shape

- GET /v1/status: protocol version, app version, snapshot revision, and server time. No medical details.
- POST /v1/pair/request: starts a pairing request.
- POST /v1/pair/confirm: confirms the short-lived code and returns the client credential.
- GET /v1/snapshot: returns a consistent, versioned snapshot of medications, schedules, dose logs and one-off dose logs.
- POST /v1/commands: submits one supported write command. Android validates and commits it to Room before returning success.
- GET /v1/changes?after=<revision>: returns changes since a revision, or asks the client to download a new snapshot when the revision is too old.

All authenticated responses include a monotonically increasing snapshot revision. Writes must include an idempotency key so retrying after a dropped response cannot record the same dose twice.

## Offline desktop behavior

The desktop client stores the last valid snapshot and the timestamp it was received. While disconnected it is strictly read-only. It must not queue write commands. A failed or partial refresh must never replace the last valid snapshot.

## Android lifecycle

- Companion is off by default and can be stopped by the user.
- When enabled, use a user-visible foreground service and a persistent notification.
- Respect Android background restrictions and local-network permission requirements for the target SDK.
- Stop discovery and close the listener immediately when Companion is disabled.
- Do not run a listener at boot without explicit user opt-in.
