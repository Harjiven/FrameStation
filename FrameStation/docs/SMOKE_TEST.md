# FrameStation On-Device Smoke Test

CI (`.github/workflows/build.yml`) verifies compilation and JVM unit tests. It **cannot**
verify XR input, video streaming, or process/notification behavior. Those must be checked
by hand on a real Samsung Galaxy XR headset (or compatible Android XR device) before any
release. Run this checklist after every wave that touches streaming, input, services, or the
manifest.

**Environment:** Galaxy XR (Android 14+), a Wi-Fi 5+ network, and at least one PC running
Sunshine/Apollo. For multi-stream and per-host tests, have **two** host PCs (or two Sunshine
instances) reachable.

Record: build commit, device, date, and pass/fail per case.

---

## 1. Pairing & hosts
- [ ] 1.1 Pair host A via PIN — pairing completes, host appears in Host Manager, marked paired.
- [ ] 1.2 Pair host B — succeeds independently; `server_{hostId}.crt` written per host (Task 1.3).
- [ ] 1.3 Switch active host A↔B — server address + MAC + quality profile update.
- [ ] 1.4 Wake-on-LAN — chip appears when MAC set; magic packet sent (host wakes if configured).

## 2. Single stream (main panel = :stream0)
- [ ] 2.1 Start stream to host A — connects, video renders, status shows "Connected".
- [ ] 2.2 App selection — pick a non-Desktop app; that app streams (not just Desktop).
- [ ] 2.3 Toolbar **Stop** during active stream — stream stops (regression guard for A1/M1.1).
- [ ] 2.4 Toolbar **Mute/unmute** during active stream — audio toggles without restart.
- [ ] 2.5 **Keyboard** button during active stream — typing bar opens and sends text.
- [ ] 2.6 Toggle desktop panel off while streaming — stream actually stops (no orphaned decoder; M1.1d).

## 3. Input completeness (Task 1.6 area)
- [ ] 3.1 Touch/ray **left-click** on main video panel moves + clicks the PC cursor.
- [ ] 3.2 **Right-click** works.
- [ ] 3.3 **Scroll** gesture scrolls on the PC (`sendMouseScroll`).
- [ ] 3.4 **Hardware BT keyboard** letters/numbers type into the PC.
- [ ] 3.5 **Ctrl+C / Ctrl+V** and Alt+Tab work (modifiers via `translateModifiers`).
- [ ] 3.6 Panel keeps input focus after connect (keyboard keeps working after clicking elsewhere and back).

## 4. Multi-stream & resolution ladder (Tasks 1.6 / 2.6)
- [ ] 4.1 Two streams, **both at 4K** — both render; no cap applied.
- [ ] 4.2 Open a **third** stream — confirm dialog appears: main stays 4K, others → 1440p.
- [ ] 4.3 Confirm — main panel verifiably remains 4K; the two secondaries run at 1440p.
- [ ] 4.4 If device can't sustain 4K+1440p+1440p — secondaries **auto-degrade to 1080p** (fallback rung).
- [ ] 4.5 "Don't ask again" checkbox — future 3rd streams cap silently; survives app restart.
- [ ] 4.6 Close one stream (back to 2) — remaining streams **restore** to full/4K.
- [ ] 4.7 A 3rd stream the device truly cannot run — shows a clear message, no black panel / crash.

## 5. Background survival & notification (Task 2.7)
- [ ] 5.1 With 1 stream active, background the app — **one aggregated notification** shows active streams.
- [ ] 5.2 Stream **audio/video continues** in background (best-effort).
- [ ] 5.3 Foreground again — stream still connected, controls still work.
- [ ] 5.4 Memory-pressure: with 3 streams backgrounded, open a heavy app — worker processes survive
        (or fail gracefully). Note actual behavior — this validates the binding-importance assumption.
- [ ] 5.5 Notification aggregates correctly as streams open/close (count/labels update).

## 6. Resilience
- [ ] 6.1 Auto-reconnect: drop Wi-Fi mid-stream, restore it — stream resumes within backoff window.
- [ ] 6.2 Reconnect gives up after 5 failed attempts (no infinite loop) — shows manual Reconnect (M1.5).
- [ ] 6.3 Cancel button during reconnect works.
- [ ] 6.4 Auth/cert error (stream unpaired host) — shows "Not paired", does NOT auto-retry.

## 7. Browser panels (security regressions)
- [ ] 7.1 Plex bookmark plays protected media (DRM permission + desktop UA).
- [ ] 7.2 Spotify keeps login across restart (third-party cookies).
- [ ] 7.3 HTTPS pages still load after mixed-content downgrade (QW3) — no broken Plex.
- [ ] 7.4 New-tab URL bar: https normalization + Google search fallback.

## 8. Non-XR fallback (Task 2.5, pre-Play)
- [ ] 8.1 Install on a non-XR Android phone — `FallbackWorkspace` renders (no crash).
- [ ] 8.2 Configure server address in fallback UI persists.

---

### Sign-off
| Field | Value |
|---|---|
| Build commit | |
| Device / OS | |
| Date | |
| Result | PASS / FAIL |
| Notes | |
