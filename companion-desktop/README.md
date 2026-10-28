# Dosey Companion Desktop

Standalone Java 21 desktop client for the local Dosey Companion protocol.

## Current implementation slice

- Manual LAN host/port entry (discovery can be added once the secure flow is verified).
- Explicit SHA-256 certificate pinning. Copy the fingerprint shown by the Android Companion service; the client refuses the TLS connection if the presented certificate differs.
- Pairing request, Android approval polling, one-time credential issuance, and authenticated snapshot refresh.
- The credential is kept in memory only in this first slice. Do not treat this as release-ready: persistent credential storage and encrypted last-good snapshot caching are still to be implemented.

## Build on Windows

From the repository root:

```powershell
cd companion-desktop
gradle run
```

If Gradle is not installed globally, use the repository's wrapper from this directory:

```powershell
..\gradlew.bat run
```

The desktop app requires JDK 21 or newer. The repository wrapper may use a Gradle version configured for the Android project; if the Java plugin is not supported by that wrapper, use a compatible Gradle 8.x installation or add a dedicated wrapper for this subproject.

## Security notes

- Never connect using a fingerprint that has not been compared against the fingerprint displayed by Android.
- The client disables redirects and uses the pinning trust manager for every HTTPS request.
- The current UI does not persist credentials, medication snapshots, or queued commands.
- Offline read-only cache, robust service discovery, write-command confirmation UX, and integration tests are follow-up work before release.
