# FrameStation Hardening & Play-Readiness Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: use `executing-plans` / `subagent-driven-development` to implement this plan task-by-task.

**Goal:** Take FrameStation from a functional prototype to a Play-Store-ready, single-code-path streaming app: fix the orphaned stream lifecycle, unify on the IPC streaming path, close the Play-blocking security holes, and add the 3-stream / background-survival features — all behind a CI verification harness.

**Architecture:** The keystone change is retiring the in-process (`MoonlightStreamManager`-in-composable) streaming path and routing **all** streams — including the main desktop panel — through the isolated `:stream0/1/2` service processes over AIDL. Once there is a single streaming API surface, touch-to-mouse, hardware keyboard, per-host certs, mute, and typing all light up uniformly, and the 3-stream resolution ladder + foreground-service survival become tractable.

**Tech Stack:** Kotlin 2.3.0, AGP 9.0.0, Jetpack Compose for XR (alpha), AIDL multi-process services, embedded moonlight-core (prebuilt `libmoonlight-core.so`, arm64-v8a), OkHttp, JUnit4 + kotlinx-coroutines-test.

---

## CRITICAL CONSTRAINT: verification model

The authoring environment has **no local JDK/Android SDK** — Gradle cannot run here. Therefore:

- **CI is the verification harness.** Task 0.1 stands up GitHub Actions (`assembleDebug` + `testDebugUnitTest`). Every subsequent code task is "done" only when CI is green on push.
- **XR input/streaming cannot be unit-tested** (no XR emulator). Those are verified via `docs/SMOKE_TEST.md` on a real Galaxy XR device.
- Pure logic (state machines, resolution-ladder decisions, serializers, capacity math) **must** be extracted into plain classes and JVM-unit-tested — that is where our automated confidence comes from.

**Definition of done (whole plan):**
- CI red on compile error / unit-test failure / (eventually) lint error.
- Toolbar Stop/Mute/Keyboard work during an active stream (smoke test).
- One streaming path; `StreamController` callback-holder deleted; `grep translateKeyCode app/src` resolves to real call sites.
- No `X509TrustManager` whose `checkServerTrusted` is empty anywhere in the shipped APK (incl. moonlight-core).
- 3 streams = main ≤4K + two capped to 1440p (fallback 1080p), gated by a confirm dialog with persisted auto-approve.
- Streams survive backgrounding behind one aggregated `mediaPlayback` notification (best-effort).

---

## Dependency / wave map

```
Wave 1  (parallel, no deps)         : 0.1 CI · 0.3 SMOKE_TEST · 3.1 docs · QW3 mixed-content · QW5 logging
Wave 2  (needs CI green)            : 0.2 characterization tests (extract StreamSessionState)
Wave 3  (keystone, serial)         : 1.1 hoist lifecycle → :stream0  (XL, broken into 1.1a–1.1d)
Wave 4  (parallel, need Wave 3)    : 1.2 AIDL parity · 1.3 per-host certs · 1.5 reconnect contract
Wave 5  (parallel, need Wave 4)    : 1.4 TLS pinning+Keystore · 1.6 decoder guard · touch+keyboard
Wave 6  (parallel, need Wave 5)    : 2.1 StreamSession unify · 2.2 lazy binding · 2.6 1440p ladder · 2.7 FGS
Wave 7  (polish/launch)            : 2.3 VM tests · 2.4 detekt · 2.5 finish fallback · 3.x · launch checklist
```

Rule: never start a wave until the prior wave's CI is green. Within a wave, dispatch one subagent per disjoint file-set.

---

## WAVE 1 — Safety net & correct-by-inspection quick wins

### Task 0.1 — CI pipeline (verification harness)
**Files:** Create `.github/workflows/build.yml` (repo root, NOT under `FrameStation/`).
**Notes:** Gradle project root is `FrameStation/`; run gradlew with `working-directory: FrameStation`. moonlight-android submodule is **not** needed for the build (moonlight-core is in-tree) — do not fetch submodules. AGP 9.0.0 is bleeding-edge; the first run may need Gradle/JDK tuning — that is expected and is exactly what CI is for.
**Acceptance:** A PR with a deliberately broken test turns the `build` job red; a clean tree is green. `lint` runs as a **non-blocking** job initially (pre-existing warnings must not block Wave 1).
**Verification:** push branch → observe Actions run.

### Task 0.3 — Smoke-test checklist
**Files:** Create `FrameStation/docs/SMOKE_TEST.md`.
**Content:** on-device cases that CI cannot cover — pairing (host A + host B with per-host cert), single stream start/stop, mute during stream, hardware keyboard (incl. Ctrl+C), touch-to-mouse (left/right/scroll), soft typing bar, 2×4K streams, 3-stream ladder (main 4K + 2×1440p, confirm dialog, auto-approve checkbox, 1080p fallback rung), background survival + aggregated notification, memory-pressure survival, WoL, auto-reconnect on Wi-Fi drop.
**Acceptance:** file exists, every Milestone-1/2 behavior has a numbered, checkable case.

### Task 3.1 — Docs reconciliation (README + FEATURES)
**Files:** Modify `README.md`, `FrameStation/docs/FEATURES.md`.
**Spec:** tag every "Current Feature" as **Working / Partial / Planned** against the code:
- Move to **Planned/Partial**: "Touch-to-Mouse Mapping" (main panel no-ops — `StreamVideoSurface.kt:61`, `SpatialWorkspace.kt:373`), "Hardware Keyboard Forwarding" (`translateKeyCode` uncalled — `NativeStreamPanel.kt:612`), "Scroll Support" (never sends `sendMouseScroll`).
- Correct **under-claims**: Non-XR fallback exists (`FallbackWorkspace.kt`, `XRWorkspaceApp.kt:65`); gamepad capture exists (`NativeStreamPanel.kt:189-252`); AV1 is advertised in AUTO (`MoonlightStreamManager.kt:235-247`).
- Add a one-line note atop `FEATURES.md` that README is canonical.
**Acceptance:** every claim maps to a cited code path or is labelled Planned.

### QW3 — Downgrade WebView mixed-content mode
**Files:** `BookmarkWebViewPanel.kt:274`, `DesktopStreamPanel.kt:42`.
**Change:** `MIXED_CONTENT_ALWAYS_ALLOW` → `MIXED_CONTENT_COMPATIBILITY_MODE`.
**Acceptance:** correct-by-inspection; Plex still loads (smoke test later).

### QW5 — Log swallowed IPC broadcast exceptions
**Files:** `StreamService.kt:172,180,188` (and `WorkspaceViewModel.kt:533` teardown).
**Change:** replace `catch (_: Exception) {}` with a `Log.w(TAG, ...)` so binder-death races are visible.
**Acceptance:** no empty catch in broadcast loops.

---

## WAVE 2 — Characterization tests (net under the refactor)

### Task 0.2 — Extract `StreamSessionState`
**Files:** Create `streaming/StreamSessionState.kt`; Test `test/.../streaming/StreamSessionStateTest.kt`.
**Why:** the connect/terminate/reconnect/`isStreaming` decisions are currently smeared across `NativeStreamPanel` callbacks (`NativeStreamPanel.kt:160-187,290-341`) and `WorkspaceViewModel` (`setStreamingState`, `toggleDesktopPanel:250-256`). Extract them into a pure state machine and pin current behavior **before** moving ownership.
**TDD steps (per transition):**
1. Write failing test: `connectionStarted` → `Connected`, cancels reconnect, emits `isStreaming=true`.
2. `connectionTerminated(intentional=false, autoReconnect=true)` → triggers reconnect path.
3. `toggleDesktopPanel while streaming` → must **stop** the session (documents current leak `WorkspaceViewModel.kt:250-256`, then fixes it in 1.1).
4. `stopStream` (user) → `Disconnected`, no reconnect.
**Acceptance:** ≥10 behavioral tests green in CI; no production wiring changed yet (pure extraction + tests).

---

## WAVE 3 — Keystone: hoist stream lifecycle to `:stream0` (Task 1.1, XL)

**Principle:** UI renders state; the service owns the connection. The main desktop panel becomes the first IPC slot (`:stream0`), eliminating the local path and the four-decoder ambiguity.

**1.1a — ViewModel owns a `MainStreamSession`.** Add a ViewModel-scoped session wrapping `StreamServiceConnection(":stream0")`, exposing `StateFlow<StreamUiState>` (status text, connecting/connected, error) + `start/stop/mute/sendText/reconnect`. The `Surface` from `StreamVideoSurface` is delivered via a setter (`WorkspaceViewModel.kt` init already pre-binds slots — reuse `getStreamSlot`).
**1.1b — `NativeStreamPanel` becomes a renderer.** Delete the `remember { MoonlightStreamManager(...) }` (`NativeStreamPanel.kt:160`) and all `streamManager?.` calls; the panel reads `StreamUiState` and forwards intents to the ViewModel. Delete `StreamController` (`NativeStreamPanel.kt:599-607`) and the null-callback dispose dance (`:281-287,358-365`).
**1.1c — Toolbar calls ViewModel directly.** Replace `streamController.stopStream()/showKeyboard()` (`SpatialWorkspace.kt:510-511`) with `viewModel::stopStream` / `viewModel::toggleKeyboard`.
**1.1d — Fix `toggleDesktopPanel` leak.** It must stop the session, not just flip `isStreaming` (`WorkspaceViewModel.kt:250-256`).
**Gotchas:** `MoonlightStreamManager` must be created on the main thread (`StreamService.kt:68`) — already handled inside the service; keep the "surface not ready" guard (`NativeStreamPanel.kt:293-297`); dispose becomes a no-op for the session (it no longer owns anything).
**Acceptance:** Stop/Mute/Keyboard work while streaming (smoke); `grep StreamController app/src` empty; `StreamSessionStateTest` still green; CI green.

---

## WAVE 4 — API-surface parity (parallel)

### Task 1.2 — AIDL parity
**Files:** `IStreamService.aidl`, `StreamService.kt`, `StreamServiceConnection.kt`, `MoonlightStreamManager.kt`.
Add `setMuted(boolean)`, `sendUtf8Text(String)`, extend `startStream(..., int appId, String certFileName)`. Route the typing bar + mute through the connection.
**Gotcha:** prefs are per-process and **not** multi-process-safe — pass the cert **filename** (shared `filesDir`), never cert bytes or host config, across the binder. Client/service AIDL versions must ship together.
**Acceptance:** IPC stream mutes, accepts typed text, streams the selected app.

### Task 1.3 — Per-host certs end-to-end
**Files:** `WorkspaceViewModel.kt:486-489,557-579`, `StreamService.kt:79`, `ServerManager.kt:177`.
Pass `host.certFileName` through `openStream → startStream → ServerManager.activeCertFileName`; set it in `fetchApps` too.
**Acceptance:** unit test on cert-filename resolution; smoke: pair host B → stream host B succeeds with `server_{id}.crt`.

### Task 1.5 — Real reconnect contract
**Files:** `AutoReconnectManager.kt:157-166`, `MoonlightStreamManager.reconnect()` (`:467-473`), session owner from 1.1.
Make the reconnect action await an actual connection result (bridge `connectionStarted`/`connectionTerminated` into a `suspendCancellableCoroutine`) so `maxRetries` is real.
**Acceptance:** test — 5 failed attempts → `Failed`, no 6th.

---

## WAVE 5 — Security + input completeness (parallel)

### Task 1.4 — TLS pinning (TOFU) + Keystore creds  **[Play release gate]**
**Files:** `SunshineApiManager.kt:164-191`, `ServerManager.kt:148-155`, moonlight-core `NvHTTP` (per `MODIFICATIONS.md`), new `security/CredentialStore.kt`, `WorkspaceViewModel.kt:788-794`, `AndroidManifest.xml:14`.
Replace trust-all `X509TrustManager`s with a pinning manager that **throws** on mismatch (reuse the pairing TOFU cert), key the cached OkHttp client on host+pinned cert, encrypt Sunshine creds with a Keystore AES key, set `allowBackup="false"`.
**Why gate:** Google Play's ASI program flags empty `checkServerTrusted` / always-true `HostnameVerifier` (developer.android.com/privacy-and-security/risks/unsafe-trustmanager) — and the vendored `NvHTTP` ships in the APK, so it is scanned too.
**Acceptance:** no unconditional trust manager anywhere; pinning unit test; creds unreadable in prefs XML.

### Task 1.6 — Decoder-concurrency guard  **[3-stream launch feature]**
**Files:** `WorkspaceViewModel.openStream` (`:557-579`), new `streaming/DecoderCapacity.kt` + test.
Probe `MediaCodecInfo.CodecCapabilities.getMaxSupportedInstances()` / performance points before assigning a slot; if even the 1080p fallback rung is impossible, refuse the 3rd stream with a clear message (no black panel / native crash).
**Acceptance:** pure decision fn unit-tested; smoke: unsupported 3rd stream shows a message.

### Touch-to-mouse + hardware keyboard (falls out of 1.1/1.2)
**Files:** `StreamVideoSurface.kt:58-89` (add right-click + `sendMouseScroll`), `NativeStreamPanel.kt:371-386,612-725` (wire `translateKeyCode`/`translateModifiers` into `onKeyEvent`, make the panel `focusable()` + request focus on connect).
**Acceptance:** smoke cases: left/right-click, scroll, Ctrl+C via BT keyboard, focus retained.

---

## WAVE 6 — Feature build-out (parallel)

### Task 2.1 — Single `StreamSession` abstraction
Wrap the (now sole IPC) path; `NativeStreamPanel` consumes only it; nullability noise (Q3) gone. Delete the local `MoonlightStreamManager`-in-panel entirely and `DesktopStreamPanel` + `useNativeStreaming` toggle (`SpatialWorkspace.kt:145,431`).

### Task 2.2 — Lazy slot binding
Bind `:streamN` on first `openStream`, unbind on close (`WorkspaceViewModel.kt:227-245`). Verify cold start spawns 1 process via `adb shell ps`.

### Task 2.6 — Asymmetric 3-stream 1440p ladder  **[L]**
**Files:** `WorkspaceViewModel.kt`, `WorkspaceUiState`, new dialog composable, `SpatialWorkspace.kt`, `SettingsDialog.kt`, new `streaming/ResolutionPolicy.kt` + test.
Primary = main desktop stream if active, else first-opened (confirmed). At exactly 3 streams: main keeps ≤4K, the two secondaries request 1440p (preferred rung); on decoder/config failure degrade **both** secondaries to 1080p; restore on drop to ≤2. Confirm dialog + persisted `auto_approve_multistream_cap` (checkbox), surfaced read-only in Settings.
**Acceptance:** `ResolutionPolicy` decision fn unit-tested (which slot → which rung, when to degrade); smoke: both rungs, main verifiably 4K.

### Task 2.7 — Aggregated foreground service  **[background survival]**
**Files:** new `StreamCoordinatorService` (main process), `AndroidManifest.xml`, `StreamServiceConnection`/`WorkspaceViewModel` wiring; remove background-stop (`NativeStreamPanel.kt:267-271` — already gone after 1.1).
Single `mediaPlayback` FGS in the **main** process owns one aggregated notification updated from per-slot IPC state; `:streamN` stay bound workers (kept alive by binding importance to the FGS process). Add `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` perms.
**Gotcha/caveat:** binding-importance survival is strong but not absolute under memory pressure — smoke-test the memory-pressure case; fallback is per-process FGS notifications.
**Acceptance:** one notification reflects all streams; streams continue through background (best-effort) per smoke test. One Play Console FGS declaration.

---

## WAVE 7 — Polish & launch checklist

- **2.3** `WorkspaceViewModel` testability: inject managers via constructor default args; tests for host CRUD / active-host / slot allocation (≥70% on that logic).
- **2.4** detekt (unused-code + complexity) gated in CI; flip Wave-1 `lint` job to blocking.
- **2.5** Finish `FallbackWorkspace` (non-XR pairing/streaming usable) — matters because Play listing installs on non-XR phones (`uses-feature ... required="false"`).
- **3.2** Verify/drop `jcodec` (`moonlight-core/build.gradle.kts`).
- **3.4** Group `SpatialWorkspace`'s 37 params into action interfaces (`SpatialWorkspace.kt:97-139`).
- **3.5 / Q5** Pin the exact moonlight-common-c/moonlight-android commit + a reproducible `.so` build recipe in `MODIFICATIONS.md` (GPL §6 + Play-rebuild insurance). *(Note: current `.so` is already 16 KB-page aligned — verified — so no immediate Play blocker there.)*
- **Launch:** Data Safety form; R8 `isMinifyEnabled=true` + keep-rules for `MoonBridge`/AIDL/reflective moonlight classes (`app/build.gradle.kts:19-27`); Play Console FGS declaration; release signing.

---

## Retirements (do not preserve as "fallbacks")
- Local in-process streaming path (`MoonlightStreamManager` inside `NativeStreamPanel`) — root of the divergence bugs.
- `DesktopStreamPanel` WebView + `useNativeStreaming` toggle — a WebView cannot do low-latency Moonlight.
Keep and finish instead: the **non-XR `FallbackWorkspace`** (Task 2.5).

## Open items resolved in this plan
- Primary-stream edge case: primary = main desktop stream if active, else first-opened. **Confirmed.**
- Cap scope: asymmetric, secondaries only, at 3 streams. **Confirmed.**
- Notification: single aggregated (coordinator FGS). **Confirmed.**
- Background survival: best-effort via FGS. **Confirmed.**
