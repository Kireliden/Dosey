# Dosey Companion — local connection architecture

## Product rules

- Android is the source of truth for medication data.
- The PC keeps a local read-only snapshot for offline viewing.
- Writes from the PC are allowed only while a live, authenticated connection to the phone is available.
- The first version works only when both devices can reach one another on the same local network (Wi-Fi or Ethernet, including a phone hotspot).
- No cloud provider APIs, accounts, or internet relay are required.
- Backups remain a separate portable-file feature.

## Connection flow

1. The user explicitly enables Companion on Android.
2. Android advertises a Dosey service on the local network using Android Network Service Discovery (NSD).
3. The PC discovers the service and the user approves a pairing request on the phone.
4. The devices establish an authenticated, encrypted session.
5. The PC downloads a versioned snapshot and stores it locally.
6. While connected, the PC reads current data and sends write commands to Android. Android validates and commits all writes to Room before acknowledging them.
7. The PC refreshes its local snapshot after confirmed changes. When disconnected, it exposes only that snapshot and disables all write actions.

## Safety and correctness requirements

- Never expose medication data over plaintext HTTP/TCP.
- Discovery metadata must not contain personal or medication data.
- Do not bind the service to external interfaces beyond the local network where possible.
- Pairing must be explicit, revocable, and authenticated; unknown clients must not be able to read or modify data.
- The PC must only report a write as successful after the phone confirms the Room transaction.
- The local snapshot must include a last-successful-sync timestamp and remain intact when a refresh fails.
- No offline write queue in the first version.
- Android remains usable when Companion is disabled or unavailable.
- Android background execution limits must be respected; any active connection service must be user-visible and stoppable.

## Implementation milestones

1. Android local-network permissions, opt-in service lifecycle, secure pairing and service discovery.
2. Read-only health/status endpoint and protocol tests.
3. Snapshot export and PC-side local cache (implemented with the future desktop client).
4. Read-only PC view.
5. Authenticated write commands and refresh-after-commit.
6. Disconnect/read-only UI and recovery tests.

The desktop client is intentionally out of scope for the Android-only foundation. Do not merge this work into the default branch until the user has built and validated it locally.

### Current implementation status

- Added an explicit PC Companion navigation screen and enable/disable control.
- Added a user-started foreground service with a persistent notification; it does not auto-start.
- The foreground service now provisions a persistent Android Keystore RSA key/certificate, opens a TLS 1.2/1.3 listener on an ephemeral port, and advertises `_dosey._tcp.` through NSD while enabled.
- Android computes and displays the TLS certificate SHA-256 fingerprint; `GET /v1/status` includes it for diagnostics. The PC must still calculate the fingerprint from the TLS handshake certificate and pin that certificate after the user compares it with Android.
- `GET /v1/status`, `POST /v1/pair/request`, and `GET /v1/pair/status?id=...` are implemented. Pair requests use a six-digit comparison code, expire after 120 seconds, and require explicit Android approval or rejection.
- Approval state is in-memory and cleared when Companion stops. No credentials are issued and no medication data or write endpoints are exposed yet.
- TLS fingerprint verification/pinning UX, revocable client credentials, authenticated snapshot transfer and write commands are still pending. The future PC client must explicitly pin the certificate during pairing; it must not disable TLS verification globally.
- The service currently binds the TLS socket to the platform's default server interface selection; because the only route is non-sensitive status, this is a bootstrap milestone rather than the finished security boundary. Before exposing any medication data, implement and test authenticated pairing, access controls, rate limits, and network-interface restrictions.
