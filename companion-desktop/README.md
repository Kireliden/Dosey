# Dosey Companion Desktop

Standalone Java 21 desktop client for the local Dosey Companion protocol.

## Current implementation slice

- Automatic local-network discovery using the `_dosey._tcp.local.` service.
- The initial discovery request reads only the public status endpoint to learn the certificate fingerprint. It does not send credentials or request medication data.
- The desktop then pins the exact SHA-256 certificate for every subsequent HTTPS request.
- QR pairing: the PC displays a QR containing the discovered endpoint, pinned fingerprint, short-lived request ID, computer name, and six-digit verification code.
- On Android, open **PC Companion**, enable the service, and tap **Scan PC pairing QR** on the pending request. The app verifies that the QR fingerprint matches its own TLS certificate and that the request ID and code match the pending request before approving it.
- Pairing approval remains explicit. The QR alone does not grant access to medication data.
- Authenticated snapshot refresh is available after pairing.

## Build on Windows

From the repository root:

```powershell
cd companion-desktop
..\gradlew.bat compileJava
..\gradlew.bat run
```

The desktop app requires JDK 21 or newer.

## Security and known limitations

- The bootstrap status request deliberately uses an untrusted TLS connection only to retrieve the server's advertised certificate fingerprint. It must not be used for sensitive data; the actual client connection is immediately re-established with strict certificate pinning.
- The Android QR scanner independently compares the QR fingerprint with the certificate held by the Android service, and checks the pending request ID, computer name and six-digit code.
- The credential is currently held in memory only. Secure persistent credential storage and encrypted last-good snapshot caching are not implemented yet.
- Offline read-only cache, command UX, reconnection polish, and integration tests remain follow-up work before release.
- This implementation has not yet been verified in a live PC-to-phone pairing session.
