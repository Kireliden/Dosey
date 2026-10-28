# PC Companion — development status

**Status:** paused on 28 October 2026. The main Dosey app takes priority. The PC Companion screen is temporarily covered by a blurred “Comming soon” overlay; companion implementation code is intentionally retained for later work.

**Repository:** `Kireliden/Dosey`  
**Working branch:** `feature/companion-local-connection`  
**Local checkout:** `C:\\Dev\\Android\\Dosey`  
**Important:** work has been isolated to this feature branch; `master` has not been merged or changed as part of this work.

## 1. Goal and current scope

PC Companion is intended to connect the Android Dosey app to a standalone desktop client on the same local network. Android remains the source of truth. The initial work includes discovery, a TLS-protected local API, QR-based pairing, and an initial authenticated medication-summary read. Secure credential persistence, encrypted offline cache, robust discovery/sync tests, and a working end-to-end connection are not complete.

## 2. What has been implemented

### Android

- Foreground service starts/stops the local Companion server.
- Local service discovery uses Android NSD and advertises `_dosey._tcp.`, with a generated instance name and protocol attribute.
- `GET /v1/status` returns the protocol version and SHA-256 certificate fingerprint.
- Self-signed TLS certificate and private key are created in Android Keystore; the server currently restricts connections to TLS 1.2.
- Local-network client address checks and request size/line/header limits are present.
- Pairing requests expire after 120 seconds. The phone can scan the desktop QR, compare the QR fingerprint to its local certificate, and approve/reject a request with a six-digit verification code.
- Approved desktop clients are stored/revocable through `CompanionCredentialStore`.
- `GET /v1/changes` currently returns a full snapshot with a reset marker; this is **not** a true incremental change feed.
- Camera permission was added for QR scanning.
- Diagnostic logging was added around the TLS handshake under tag `DoseyCompanionServer`.

### Desktop client

Location: `companion-desktop/src/main/java/com/borealis/dosey/desktop/DoseyCompanionApp.java`

- Standalone Java 21 Swing client.
- Uses JmDNS for `_dosey._tcp.local.` discovery and searches across local IPv4-capable network interfaces.
- Has manual host/IP and port fields, “Find Dosey on network”, “Verify certificate & connect”, “Request pairing”, and “Refresh snapshot” actions.
- Reads `/v1/status` to bootstrap the server certificate fingerprint, then uses strict SHA-256 certificate pinning for subsequent requests.
- Displays a QR pairing request; the phone scans it and approves the request; the desktop polls for approval and confirms pairing.
- Pairing credentials are held in memory only. Persistent credential storage and offline cache are not implemented.
- Desktop dependencies include `org.json:json:20250517`, ZXing 3.5.3 and JmDNS 3.6.3. Android uses ZXing Embedded 4.3.0.

**Security caveat to revisit:** the desktop's initial fingerprint-bootstrap request uses a trust-all TLS manager only to read `/v1/status` and obtain the fingerprint. It is not intended to send credentials or medication data in that bootstrap request. All later requests must remain strictly pinned. Review this bootstrap design before calling the connection production-ready.

## 3. Changes and troubleshooting history

The following commits were made on `feature/companion-local-connection` during the work:

| Commit | Change / reason |
| --- | --- |
| `4a93729c` | QR pairing flow work. |
| `329a78e0` | Improve mDNS discovery across Windows network interfaces. |
| `650e4338` | Fix missing `try` block in network discovery. |
| `870e9b9d` | Fix network interface address stream check (`inetAddresses()` returns a stream, not an enumeration). |
| `5449ebbe` | Prefer IPv4 address for companion HTTPS bootstrap. |
| `d4901b8c` | Fix diagnostic `TAG` scope in companion server after a compile error reported unresolved `TAG` at lines 157, 165 and 403. |
| `29a15f7f` | Prefer compatible TLS 1.2 cipher suites. Restricts enabled suites to ECDHE-RSA with GCM or CHACHA20. |
| `aed51f9d` | Allow Android Keystore raw RSA TLS signing operation; changed key policy and versioned alias to `dosey_companion_tls_v2`. |
| `32933bdf` | Temporarily gate the PC Companion screen behind the blurred “Comming soon” overlay. |

### Build results actually reported

- Android: `cd C:\\Dev\\Android\\Dosey` then `.\gradlew.bat assembleDebug` — **BUILD SUCCESSFUL** before the final TLS key-policy changes.
- Desktop: `cd C:\\Dev\\Android\\Dosey\\companion-desktop` then `..\gradlew.bat compileJava` — **BUILD SUCCESSFUL**, with a deprecated API note.
- Desktop `..\gradlew.bat run` launched the app. It printed an SLF4J warning that no provider was found; this was non-fatal.
- After fixing the discovery compile error, the user reported desktop compilation succeeded.
- The latest TLS change and the UI overlay have **not yet been confirmed by a build in the user's environment**. Rebuild before relying on them.

## 4. Real-device test and observed failure

Test setup reported by the user:

- Android Companion service was running.
- Phone and Windows PC were connected to the same router Wi-Fi.
- No Windows Java firewall prompt appeared.
- Desktop discovery failed instead of finding the phone.

After the discovery changes, the reported desktop error became:

```
Discovery failed: remote host terminated the handshake
```

The Android Logcat then showed the following repeated failure during `CompanionNetworkServer.handleClient(...)` at the call to `SSLSocket.startHandshake()`:

```
CryptoUpcalls W Preferred provider doesn't support key:
java.security.InvalidKeyException: Keystore operation failed
Caused by: android.security.KeyStoreException: Incompatible padding mode
CryptoUpcalls W Could not find provider for algorithm: RSA/ECB/NoPadding
DoseyCompanionServer E TLS handshake failed from 192.168.1.28; enabled protocols=TLSv1.2
javax.net.ssl.SSLHandshakeException: ... RSA routines ... internal error
```

The longer stack trace points through Android Conscrypt's `CryptoUpcalls.rsaOpWithPrivateKey` / `rsaSignDigestWithPrivateKey` and Android Keystore. This is a TLS private-key operation failure, not evidence of an mDNS failure by itself.

### TLS attempts made so far

1. Server was limited to TLS 1.2. The first hypothesis that TLS 1.3 / RSA-PSS compatibility was the cause was not confirmed; the same failure continued.
2. The server cipher list was restricted to TLS 1.2 suites whose names start with `TLS_ECDHE_RSA_` and contain GCM or CHACHA20, to avoid static RSA key-exchange suites that need a private-key decrypt operation. The next real-device test still showed the same `RSA/ECB/NoPadding` / `Incompatible padding mode` failure.
3. The key generation policy was then changed to `PURPOSE_SIGN or PURPOSE_DECRYPT`, `SIGNATURE_PADDING_RSA_PKCS1` plus `ENCRYPTION_PADDING_NONE`, and the key alias changed from `dosey_companion_tls` to `dosey_companion_tls_v2` to force a fresh key with the revised policy. The user tested again and the same error remained. It is not yet established whether the installed APK definitely used the new alias/key; verify this before concluding the policy change was actually exercised.

A separate set of phone logs appeared while activating pairing:

```
NativeTurb...ManagerJni ... E ioctl 8 failed!
TurboSchedMonitor ... E failed to find setSchedAffinity method ...
NativeTurb...ManagerJni ... E ioctl 9 failed!
```

These lines looked like device/vendor scheduler diagnostics and were not shown to be the cause of the TLS failure. No root cause was established for those lines.

## 5. Exact stopping point

As of 28 October 2026:

- Desktop discovery reaches the phone, but the TLS handshake is failing.
- The most recent Logcat still reports `Incompatible padding mode` and `RSA/ECB/NoPadding`.
- There is no successful verified desktop-to-phone connection or completed end-to-end pairing test yet.
- No persistent desktop credentials, encrypted offline cache, proper delta sync, or comprehensive automated integration tests are complete.
- The PC Companion UI has now been covered with a blur and the literal label **“Comming soon”**. The underlying implementation remains in the source so work can resume later. This UI change is not yet build-verified.

## 6. How to resume later

1. Checkout/pull `feature/companion-local-connection`; do not merge to `master` automatically.
2. Build Android first:
   ```powershell
   cd C:\\Dev\\Android\\Dosey
   git pull
   .\\gradlew.bat assembleDebug
   ```
3. Install the APK, stop and restart the Companion service, and verify that the running app really uses the `dosey_companion_tls_v2` key. If necessary, add temporary logs for the selected alias/certificate fingerprint and compare it to `/v1/status`; do not log private key material.
4. If the TLS handshake still fails, stop tweaking cipher suites blindly. Revisit how the TLS private key is generated and used. A software-generated RSA key with a self-signed certificate is one possible fallback, with careful key lifecycle handling and continued strict certificate pinning on the desktop. Assess the security trade-offs before adopting it.
5. Re-run desktop compile:
   ```powershell
   cd C:\\Dev\\Android\\Dosey\\companion-desktop
   ..\\gradlew.bat compileJava
   ```
6. Test on the same Wi-Fi and collect the complete `DoseyCompanionServer` handshake error if it fails.
7. Once transport works, verify status/fingerprint validation, QR pairing and six-digit confirmation, authenticated snapshot retrieval, revocation, and then implement persistence/cache/delta sync with tests.
8. Only when the core Dosey app is in a good state should the Companion UI overlay be removed and the integration resumed.

## 7. Current UI decision

The PC Companion menu entry remains visible so the future feature is discoverable, but the screen is blurred and covered by a translucent overlay with **“Comming soon”**. This is intentionally a temporary product decision, not a claim that Companion is ready. The core medication app takes priority.
