# Independent Root Backend

Root uses libsu 6.0.0 (Apache-2.0) to launch a non-daemon RootService.
The application communicates over a narrow AIDL interface. The worker checks the
caller UID and package/UID mapping, clears Binder calling identity and calls the
notification service as UID 0. No Shizuku or Dhizuku fallback is involved.

## Identity And Capabilities

- `su -c` must establish UID 0 before a version string is trusted.
- The active `su -v` identifies Magisk, KernelSU, APatch or an explicitly named
  fork. Installed or leftover binaries are not evidence of the active runtime.
- ReSukiSU may report `KernelSU`. Its installed manager is shown separately;
  the runtime is not guessed from an APK. Hidden Magisk managers need no package
  lookup for authorization or runtime recognition.
- Unknown implementations remain usable when authorized and capable.
- Root authorization, readable notification APIs, available write signatures and
  verified writes are separate states. Probing never modifies user channels.
- Automatic selection is Root, Shizuku, then Dhizuku. Explicit unavailable choices
  do not fall back to a different authorizer.

## Write And Lifetime

The worker preserves the existing NotificationChannel, changes importance and
tries supported typed update/create signatures. It requires a matching read-back;
an accepted Binder call alone is not success. It never deletes a user channel to
force an update. Reads are paginated; client operations are serialized. Idle
connections and the launch shell are released after 1.5 seconds. A late connection
after cancellation is immediately unbound.

## Verification

JVM tests cover version identities, rejected and unknown roots, stale binary
output, authorizer order, write verification and identity-preserving rule edits.
`RootBackendInstrumentation` is an explicit device test: it creates a disposable
channel in the separate test APK, writes importance 2, 0 and 3 through Quieta's
Root backend, verifies each read-back and deletes the test channel in `finally`.
Run with `adb shell am instrument -w
app.quieta.debug.test/app.quieta.RootBackendInstrumentation` after installing both
debug and androidTest APKs. Root authorization must be granted to Quieta first.

K40S / Android 14 / ReSukiSU is the available test device. Magisk, upstream
KernelSU, APatch and other ROMs still require their own device-matrix runs; parser
coverage and a common Root transport do not replace those tests.

Validated on K40S: both own-UID and separate-test-APK channel writes passed
(importance 2 -> 0 -> 3), including read-back and fixture deletion. The root
process exited when idle. Home retained `ROOT (KernelSU)` after inventory, and
the privilege page separately displayed installed manager `ReSukiSU`.
The test fixture is launched explicitly through instrumentation shell automation
because MIUI blocks cross-app background wakeups; the actual backend calls are
still made as Quieta, not as ADB shell. No user app channel is modified.
