# Gamepad Input Forwarding Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Forward physical gamepad (Bluetooth/USB) button presses, analog stick movements, and triggers to the host PC via Moonlight's `ControllerPacket` protocol.

**Architecture:** `NvConnection.sendControllerInput()` and all `ControllerPacket` flag constants already exist in moonlight-core. We add `sendControllerInput()` to `MoonlightStreamManager`, expose it via `IStreamService` AIDL and `StreamServiceConnection`, then handle `onKeyDown`/`onGenericMotionEvent` in `NativeStreamPanel` to map Android gamepad events (`KeyEvent.KEYCODE_BUTTON_*`, `MotionEvent.AXIS_*`) to `ControllerPacket` flags. A deadzone is applied to analog axes. XR controllers are exposed as `SOURCE_GAMEPAD` on Samsung Galaxy XR and handled identically to physical gamepads.

**Tech Stack:** Kotlin, `ControllerPacket` constants, `NvConnection.sendControllerInput()`, `InputDevice.SOURCE_GAMEPAD`, `MotionEvent.AXIS_*`

---

## Context

**`ControllerPacket` button flags (moonlight-core):**
```java
A_FLAG = 0x1000, B_FLAG = 0x2000, X_FLAG = 0x4000, Y_FLAG = 0x8000
UP_FLAG = 0x0001, DOWN_FLAG = 0x0002, LEFT_FLAG = 0x0004, RIGHT_FLAG = 0x0008
LB_FLAG = 0x0100, RB_FLAG = 0x0200
PLAY_FLAG = 0x0010, BACK_FLAG = 0x0020
LS_CLK_FLAG = 0x0040, RS_CLK_FLAG = 0x0080
SPECIAL_BUTTON_FLAG = 0x0400
```

**`NvConnection.sendControllerInput()` signature:**
```java
sendControllerInput(short controllerNumber, short activeGamepadMask, int buttonFlags,
    byte leftTrigger, byte rightTrigger,
    short leftStickX, short leftStickY, short rightStickX, short rightStickY)
```

**Files to touch:**
- Modify: `app/src/main/java/com/xrworkspace/app/streaming/MoonlightStreamManager.kt`
- Modify: `app/src/main/aidl/com/xrworkspace/app/streaming/IStreamService.aidl`
- Modify: `app/src/main/java/com/xrworkspace/app/streaming/StreamService.kt`
- Modify: `app/src/main/java/com/xrworkspace/app/streaming/StreamServiceConnection.kt`
- Modify: `app/src/main/java/com/xrworkspace/app/ui/panels/NativeStreamPanel.kt`

---

## Task 1: Add `sendControllerInput()` to `MoonlightStreamManager`

**Files:**
- Modify: `app/src/main/java/com/xrworkspace/app/streaming/MoonlightStreamManager.kt`

**Step 1: Add the method**

Find:
```kotlin
    fun setMuted(muted: Boolean) {
```
Add BEFORE this method:

```kotlin
    /**
     * Forward gamepad state to the host PC.
     *
     * @param buttonFlags Bitmask of pressed buttons using [ControllerPacket] flag constants.
     * @param leftTrigger Left trigger pressure (0–255).
     * @param rightTrigger Right trigger pressure (0–255).
     * @param leftStickX Left stick horizontal (-32767 left, 32767 right).
     * @param leftStickY Left stick vertical (-32767 up, 32767 down).
     * @param rightStickX Right stick horizontal (-32767 left, 32767 right).
     * @param rightStickY Right stick vertical (-32767 up, 32767 down).
     */
    fun sendControllerInput(
        buttonFlags: Int,
        leftTrigger: Byte,
        rightTrigger: Byte,
        leftStickX: Short,
        leftStickY: Short,
        rightStickX: Short,
        rightStickY: Short,
    ) {
        connection?.sendControllerInput(
            /* controllerNumber = */ 0,
            /* activeGamepadMask = */ 1,  // one controller active
            buttonFlags,
            leftTrigger,
            rightTrigger,
            leftStickX,
            leftStickY,
            rightStickX,
            rightStickY,
        )
    }

```

**Step 2: Add the ControllerPacket import**

Find the imports block and add:
```kotlin
import com.limelight.nvstream.input.ControllerPacket
```

---

## Task 2: Expose `sendControllerInput` via AIDL

**Files:**
- Modify: `app/src/main/aidl/com/xrworkspace/app/streaming/IStreamService.aidl`

**Step 1: Add gamepad method**

Find:
```aidl
    void sendKeyboardInput(int keyMap, int direction, int modifiers);
```
Add after it:
```aidl
    /** Forward gamepad state to the host PC. buttonFlags uses ControllerPacket flag constants. */
    void sendControllerInput(
        int buttonFlags,
        int leftTrigger,
        int rightTrigger,
        int leftStickX,
        int leftStickY,
        int rightStickX,
        int rightStickY
    );
```

---

## Task 3: Implement `sendControllerInput` in `StreamService`

**Files:**
- Modify: `app/src/main/java/com/xrworkspace/app/streaming/StreamService.kt`

**Step 1: Add the binder implementation**

Find:
```kotlin
        override fun sendKeyboardInput(keyMap: Int, direction: Int, modifiers: Int) {
            streamManager?.sendKeyboardInput(
                keyMap.toShort(), direction.toByte(), modifiers.toByte(), 0.toByte(),
            )
        }
```
Add after it:
```kotlin
        override fun sendControllerInput(
            buttonFlags: Int,
            leftTrigger: Int,
            rightTrigger: Int,
            leftStickX: Int,
            leftStickY: Int,
            rightStickX: Int,
            rightStickY: Int,
        ) {
            streamManager?.sendControllerInput(
                buttonFlags,
                leftTrigger.toByte(),
                rightTrigger.toByte(),
                leftStickX.toShort(),
                leftStickY.toShort(),
                rightStickX.toShort(),
                rightStickY.toShort(),
            )
        }
```

---

## Task 4: Add `sendControllerInput` to `StreamServiceConnection`

**Files:**
- Modify: `app/src/main/java/com/xrworkspace/app/streaming/StreamServiceConnection.kt`

**Step 1: Add the forwarding method**

Find:
```kotlin
    fun sendKeyboardInput(keyMap: Short, direction: Byte, modifiers: Byte) {
        service?.sendKeyboardInput(keyMap.toInt(), direction.toInt(), modifiers.toInt())
    }
```
Add after it:
```kotlin
    fun sendControllerInput(
        buttonFlags: Int,
        leftTrigger: Byte,
        rightTrigger: Byte,
        leftStickX: Short,
        leftStickY: Short,
        rightStickX: Short,
        rightStickY: Short,
    ) {
        service?.sendControllerInput(
            buttonFlags,
            leftTrigger.toInt(),
            rightTrigger.toInt(),
            leftStickX.toInt(),
            leftStickY.toInt(),
            rightStickX.toInt(),
            rightStickY.toInt(),
        )
    }
```

---

## Task 5: Add gamepad input handler in `NativeStreamPanel`

This is the main UI change. We need to intercept `onKeyDown`/`onKeyUp`/`onGenericMotionEvent` from gamepad devices, map them to `ControllerPacket` flags, and forward via either `streamManager` or `streamServiceConnection`.

**Files:**
- Modify: `app/src/main/java/com/xrworkspace/app/ui/panels/NativeStreamPanel.kt`

**Step 1: Add imports for InputDevice and MotionEvent**

Find:
```kotlin
import android.view.KeyEvent
```
Replace with:
```kotlin
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
```

**Step 2: Add `ControllerPacket` import**

Add to imports:
```kotlin
import com.limelight.nvstream.input.ControllerPacket
```

**Step 3: Add gamepad state variables**

Find:
```kotlin
    var surfaceRef by remember { mutableStateOf<Surface?>(null) }
```
Add after it:
```kotlin
    // Gamepad button state — accumulated across key events, sent on every change
    var gamepadButtons by remember { mutableIntStateOf(0) }
    var gamepadLT by remember { mutableStateOf(0f) }
    var gamepadRT by remember { mutableStateOf(0f) }
    var gamepadLX by remember { mutableStateOf(0f) }
    var gamepadLY by remember { mutableStateOf(0f) }
    var gamepadRX by remember { mutableStateOf(0f) }
    var gamepadRY by remember { mutableStateOf(0f) }
```

**Step 4: Add `sendGamepadState()` helper**

Find the `startStreaming()` function. Add BEFORE it:

```kotlin
    /** Send current accumulated gamepad state to the host PC. */
    fun sendGamepadState() {
        if (!isConnected) return
        val flags = gamepadButtons
        val lt = (gamepadLT * 255f).toInt().coerceIn(0, 255).toByte()
        val rt = (gamepadRT * 255f).toInt().coerceIn(0, 255).toByte()
        // Android Y-axis is inverted vs Moonlight convention (up = negative)
        val lx = (gamepadLX * 32767f).toInt().coerceIn(-32767, 32767).toShort()
        val ly = (-gamepadLY * 32767f).toInt().coerceIn(-32767, 32767).toShort()
        val rx = (gamepadRX * 32767f).toInt().coerceIn(-32767, 32767).toShort()
        val ry = (-gamepadRY * 32767f).toInt().coerceIn(-32767, 32767).toShort()

        streamServiceConnection?.sendControllerInput(flags, lt, rt, lx, ly, rx, ry)
            ?: streamManager?.sendControllerInput(flags, lt, rt, lx, ly, rx, ry)
    }
```

**Step 5: Add `mapGamepadKeyToFlag()` helper**

Add after `sendGamepadState()`:

```kotlin
    /** Map Android KEYCODE_BUTTON_* to a ControllerPacket flag, or 0 if unknown. */
    fun mapGamepadKeyToFlag(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> ControllerPacket.A_FLAG
        KeyEvent.KEYCODE_BUTTON_B -> ControllerPacket.B_FLAG
        KeyEvent.KEYCODE_BUTTON_X -> ControllerPacket.X_FLAG
        KeyEvent.KEYCODE_BUTTON_Y -> ControllerPacket.Y_FLAG
        KeyEvent.KEYCODE_BUTTON_L1 -> ControllerPacket.LB_FLAG
        KeyEvent.KEYCODE_BUTTON_R1 -> ControllerPacket.RB_FLAG
        KeyEvent.KEYCODE_BUTTON_THUMBL -> ControllerPacket.LS_CLK_FLAG
        KeyEvent.KEYCODE_BUTTON_THUMBR -> ControllerPacket.RS_CLK_FLAG
        KeyEvent.KEYCODE_BUTTON_START -> ControllerPacket.PLAY_FLAG
        KeyEvent.KEYCODE_BUTTON_SELECT -> ControllerPacket.BACK_FLAG
        KeyEvent.KEYCODE_BUTTON_MODE -> ControllerPacket.SPECIAL_BUTTON_FLAG
        KeyEvent.KEYCODE_DPAD_UP -> ControllerPacket.UP_FLAG
        KeyEvent.KEYCODE_DPAD_DOWN -> ControllerPacket.DOWN_FLAG
        KeyEvent.KEYCODE_DPAD_LEFT -> ControllerPacket.LEFT_FLAG
        KeyEvent.KEYCODE_DPAD_RIGHT -> ControllerPacket.RIGHT_FLAG
        else -> 0
    }
```

**Step 6: Handle gamepad key events in a `DisposableEffect`**

The `NativeStreamPanel` composable uses `SpatialExternalSurface` which receives `SpatialInputEvent` — but physical gamepad key events come through `onKeyDown`/`onKeyUp` on the hosting `Activity`. We intercept them via a `LocalView` `dispatchKeyEvent` override using Compose's `InteractionSource` or a key event callback.

The cleanest approach in Compose is using `Modifier.onKeyEvent` on the `Box` that wraps the panel. Add the modifier to the outermost `Box`:

Find:
```kotlin
    Box(modifier = modifier.fillMaxSize()) {
```
Replace with:
```kotlin
    Box(
        modifier = modifier
            .fillMaxSize()
            .onKeyEvent { keyEvent ->
                // Only handle gamepad sources; let keyboard events pass through
                val isGamepad = keyEvent.nativeKeyEvent.source and
                    InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
                if (!isGamepad || !isConnected) return@onKeyEvent false

                val flag = mapGamepadKeyToFlag(keyEvent.nativeKeyEvent.keyCode)
                if (flag == 0) return@onKeyEvent false

                gamepadButtons = if (keyEvent.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                    gamepadButtons or flag
                } else {
                    gamepadButtons and flag.inv()
                }
                sendGamepadState()
                true
            },
    ) {
```

**Step 7: Add `androidx.compose.ui.input.key.onKeyEvent` import**

Add to imports:
```kotlin
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.KeyEventType
```

**Step 8: Handle analog axis events via `LaunchedEffect` polling or `onGenericMotionEvent`**

Analog gamepad motion comes through `onGenericMotionEvent` on the Activity, not through Compose events. The cleanest way to capture it is to use `LocalView.current` to register a listener.

Add a `DisposableEffect` block after the existing `DisposableEffect` (around line 121):

```kotlin
    // Capture analog gamepad input (joystick axes, triggers) from the hosting view
    val localView = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(localView, isConnected) {
        val listener = android.view.View.OnGenericMotionListener { _, event ->
            if (!isConnected) return@OnGenericMotionListener false
            val isJoystick = event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
            if (!isJoystick) return@OnGenericMotionListener false

            val device = InputDevice.getDevice(event.deviceId) ?: return@OnGenericMotionListener false
            val flat = device.getMotionRange(MotionEvent.AXIS_X, event.source)?.flat ?: 0.1f

            fun getAxis(axis: Int): Float {
                val v = event.getAxisValue(axis)
                return if (kotlin.math.abs(v) > flat) v else 0f
            }

            gamepadLX = getAxis(MotionEvent.AXIS_X)
            gamepadLY = getAxis(MotionEvent.AXIS_Y)
            gamepadRX = getAxis(MotionEvent.AXIS_Z)
            gamepadRY = getAxis(MotionEvent.AXIS_RZ)
            gamepadLT = event.getAxisValue(MotionEvent.AXIS_LTRIGGER)
            gamepadRT = event.getAxisValue(MotionEvent.AXIS_RTRIGGER)

            // D-pad via hat axes
            val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            gamepadButtons = gamepadButtons
                .let { if (hatX < -0.5f) it or ControllerPacket.LEFT_FLAG else it and ControllerPacket.LEFT_FLAG.inv() }
                .let { if (hatX > 0.5f) it or ControllerPacket.RIGHT_FLAG else it and ControllerPacket.RIGHT_FLAG.inv() }
                .let { if (hatY < -0.5f) it or ControllerPacket.UP_FLAG else it and ControllerPacket.UP_FLAG.inv() }
                .let { if (hatY > 0.5f) it or ControllerPacket.DOWN_FLAG else it and ControllerPacket.DOWN_FLAG.inv() }

            sendGamepadState()
            true
        }
        localView.setOnGenericMotionListener(listener)
        onDispose {
            localView.setOnGenericMotionListener(null)
        }
    }
```

---

## Task 6: Verify and commit

**Step 1: Grep for ControllerPacket usage**
```
Grep: ControllerPacket
Path: app/src/main/java/
Expected: found in NativeStreamPanel.kt (mapGamepadKeyToFlag), MoonlightStreamManager.kt (import)
```

**Step 2: Grep for sendControllerInput**
```
Grep: sendControllerInput
Path: app/src/main/
Expected: found in MoonlightStreamManager.kt, StreamService.kt, StreamServiceConnection.kt, IStreamService.aidl, NativeStreamPanel.kt
```

**Step 3: Commit**
```bash
git add -A
git commit -m "feat: gamepad input forwarding to host PC

Map Android SOURCE_GAMEPAD KeyEvent (buttons) and SOURCE_JOYSTICK
MotionEvent (sticks, triggers, hat/d-pad) to ControllerPacket flags
and forward via NvConnection.sendControllerInput(). Add sendControllerInput()
to MoonlightStreamManager, IStreamService AIDL, StreamService binder impl,
and StreamServiceConnection IPC wrapper. Handle digital buttons via
Compose onKeyEvent modifier; analog axes via onGenericMotionListener on
LocalView. Apply per-device deadzone to stick axes. XR controllers are
exposed as SOURCE_GAMEPAD on Samsung Galaxy XR and handled identically."
```
