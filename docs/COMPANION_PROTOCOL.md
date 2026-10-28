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
- Pair requests are in-memory only and are cleared when Companion stops. A second simultaneous request is refused.
- Approval currently records consent only. It does not issue credentials, authenticate a client, or unlock medication data. The code must be compared with the PC display, but this alone is not a substitute for checking the TLS certificate fingerprint.

## Pairing

Implemented request/approval foundation:
1. User enables Companion on Android.
2. The PC requests pairing over TLS with a display name.
3. Android shows the short-lived request and six-digit comparison code.
4. The user checks that the code matches the PC and explicitly approves or rejects.
5. The PC polls the request status. Requests expire after 120 seconds and are cleared when the service stops.

Still required before a connection can access medical data:
- The PC must compute the SHA-256 fingerprint from the certificate presented during the TLS handshake, display it, and let the user compare it with Android. The PC must pin that exact certificate before treating the connection as trusted; the fingerprint returned inside `/v1/status` is informational and must not itself be trusted before certificate pinning.
- Only after the pinning UX is in place, issue a cryptographically random per-client credential after approval, store a verifier, authenticate each request, and provide per-client revocation and rate limiting.
- No credential or medical-data endpoint is implemented by this milestone.

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
