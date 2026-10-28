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
- The service intentionally does not yet open a socket or advertise an NSD endpoint. The screen states this clearly. This is a lifecycle/UI foundation, not a working PC connection.
- Next Android milestone: implement TLS certificate provisioning and the authenticated local listener, then advertise the real listener through NSD. Do not advertise a port before TLS and pairing are implemented.
