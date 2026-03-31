# XR Workspace v0.1 — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a multi-panel Android XR workspace app that streams a PC desktop (via moonlight-web-stream) alongside Spotify and a browser — all in spatial panels in Full Space mode.

**Architecture:** The app runs in Full Space on Samsung Galaxy XR. Three `SpatialAndroidViewPanel` instances are arranged in a `SpatialCurvedRow`. The main panel loads a WebView pointing at moonlight-web-stream's web client (running on the user's PC alongside Sunshine). Side panels host Spotify's web player and a general-purpose browser. An Orbiter toolbar controls panel visibility. All streaming/GPL components run server-side on the PC — the XR app is 100% proprietary.

**Tech Stack:** Kotlin, Jetpack Compose for XR (1.0.0-alpha10), Jetpack SceneCore (1.0.0-alpha11), Android SDK 36, Gradle 9.0, JDK 17. PC-side: Sunshine + moonlight-web-stream.

---

## Prerequisites

### Software Installation (Do This First)

**Development PC:**
1. Install Android Studio Canary: https://developer.android.com/studio/preview
2. Open SDK Manager, install:
   - SDK Platform: Android 16.0 (API 36)
   - SDK Build-Tools 36
   - Android Emulator + Android XR system image
3. Git: https://git-scm.com

**Gaming/Streaming PC:**
1. Install Sunshine: https://github.com/LizardByte/Sunshine/releases
2. Install latest NVIDIA drivers: https://www.nvidia.com/drivers
3. Install moonlight-web-stream: https://github.com/MrCreativ3001/moonlight-web-stream/releases
4. Run Sunshine, configure desktop capture
5. Run moonlight-web-stream, pair with Sunshine on localhost
6. Verify: open `http://localhost:8080` in a browser — you should see the moonlight-web-stream UI

**Samsung Galaxy XR:**
1. Enable Developer Mode: Settings → About → tap Build Number 7 times
2. Enable USB Debugging: Settings → Developer Options → USB Debugging
3. Connect USB-C cable, run `adb devices` — headset should appear
4. Ensure headset is on same WiFi network as streaming PC

---

## Project Structure (Final State)

```
G:\OpenCode\XR\xr-workspace/
├── app/
│   ├── build.gradle.kts
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/xrworkspace/app/
│   │   │   ├── MainActivity.kt
│   │   │   ├── ui/
│   │   │   │   ├── XRWorkspaceApp.kt           # Root composable (spatial vs non-spatial)
│   │   │   │   ├── SpatialWorkspace.kt          # Full Space multi-panel layout
│   │   │   │   ├── panels/
│   │   │   │   │   ├── DesktopStreamPanel.kt    # moonlight-web-stream WebView
│   │   │   │   │   ├── SpotifyPanel.kt          # Spotify WebView
│   │   │   │   │   └── BrowserPanel.kt          # Browser WebView + URL bar
│   │   │   │   ├── components/
│   │   │   │   │   ├── WorkspaceToolbar.kt      # Orbiter toolbar
│   │   │   │   │   └── SpaceControls.kt         # Full Space / Home Space toggle
│   │   │   │   └── theme/
│   │   │   │       ├── Theme.kt
│   │   │   │       ├── Color.kt
│   │   │   │       └── Type.kt
│   │   │   └── viewmodel/
│   │   │       └── WorkspaceViewModel.kt        # Panel visibility + settings state
│   │   └── res/
│   │       ├── values/strings.xml
│   │       ├── drawable/                         # Icons for toolbar
│   │       └── xml/                              # Backup/extraction rules
├── build.gradle.kts                              # Root build file
├── settings.gradle.kts
├── gradle.properties
└── gradle/
    └── libs.versions.toml                        # Version catalog
```

---

## Task 1: Scaffold the Android XR Project

**Files:**
- Create: `G:\OpenCode\XR\xr-workspace\` (new project root — separate from xr samples)
- Reference: `G:\OpenCode\XR\xr samples\` (copy Gradle structure, adapt)

### Step 1: Create the project directory structure

```powershell
# Run from G:\OpenCode\XR
mkdir xr-workspace
cd xr-workspace
mkdir -p app/src/main/java/com/xrworkspace/app/ui/panels
mkdir -p app/src/main/java/com/xrworkspace/app/ui/components
mkdir -p app/src/main/java/com/xrworkspace/app/ui/theme
mkdir -p app/src/main/java/com/xrworkspace/app/viewmodel
mkdir -p app/src/main/res/values
mkdir -p app/src/main/res/drawable
mkdir -p app/src/main/res/xml
mkdir -p gradle
```

### Step 2: Create `settings.gradle.kts`

```kotlin
// G:\OpenCode\XR\xr-workspace\settings.gradle.kts
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "XR Workspace"
include(":app")
```

### Step 3: Create root `build.gradle.kts`

```kotlin
// G:\OpenCode\XR\xr-workspace\build.gradle.kts
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

### Step 4: Create `gradle/libs.versions.toml`

```toml
# G:\OpenCode\XR\xr-workspace\gradle\libs.versions.toml
[versions]
agp = "9.0.0"
kotlin = "2.3.0"
arcore = "1.0.0-alpha10"
compose-xr = "1.0.0-alpha10"
scenecore = "1.0.0-alpha11"
extensionsXr = "1.2.0"
kotlinxCoroutinesGuava = "1.10.2"
composeBom = "2026.01.00"
material = "1.13.0"
activityCompose = "1.12.2"
composeRuntime = "1.10.2"
concurrentFuturesKtx = "1.3.0"
adaptiveAndroid = "1.2.0"
lifecycleViewmodelCompose = "2.9.1"

[libraries]
androidx-arcore = { module = "androidx.xr.arcore:arcore", version.ref = "arcore" }
androidx-compose-xr = { module = "androidx.xr.compose:compose", version.ref = "compose-xr" }
androidx-scenecore = { module = "androidx.xr.scenecore:scenecore", version.ref = "scenecore" }
androidx-extensions-xr = { module = "com.android.extensions.xr:extensions-xr", version.ref = "extensionsXr" }
kotlinx-coroutines-guava = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-guava", version.ref = "kotlinxCoroutinesGuava" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3" }
androidx-compose-material-icons = { module = "androidx.compose.material:material-icons-extended" }
androidx-compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
androidx-compose-runtime = { module = "androidx.compose.runtime:runtime", version.ref = "composeRuntime" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-concurrent-futures = { module = "androidx.concurrent:concurrent-futures-ktx", version.ref = "concurrentFuturesKtx" }
androidx-adaptive-android = { group = "androidx.compose.material3.adaptive", name = "adaptive-android", version.ref = "adaptiveAndroid" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycleViewmodelCompose" }
material = { module = "com.google.android.material:material", version.ref = "material" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

### Step 5: Create `app/build.gradle.kts`

```kotlin
// G:\OpenCode\XR\xr-workspace\app\build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.xrworkspace.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.xrworkspace.app"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    // Jetpack XR
    implementation(libs.androidx.arcore)
    implementation(libs.androidx.scenecore)
    implementation(libs.androidx.compose.xr)
    implementation(libs.kotlinx.coroutines.guava)
    compileOnly(libs.androidx.extensions.xr)

    // Compose
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.adaptive.android)
    implementation(libs.androidx.concurrent.futures)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.material)
}
```

### Step 6: Create `gradle.properties`

```properties
# G:\OpenCode\XR\xr-workspace\gradle.properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

### Step 7: Create `AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- G:\OpenCode\XR\xr-workspace\app\src\main\AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />

    <uses-feature android:name="android.software.xr.api.spatial" android:required="false" />

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.XRWorkspace"
        tools:targetApi="35">

        <!-- Launch directly into Full Space for immersive workspace -->
        <property
            android:name="android.window.PROPERTY_XR_ACTIVITY_START_MODE"
            android:value="XR_ACTIVITY_START_MODE_FULL_SPACE_MANAGED" />

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.XRWorkspace">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

### Step 8: Create minimal `MainActivity.kt`

```kotlin
// G:\OpenCode\XR\xr-workspace\app\src\main\java\com\xrworkspace\app\MainActivity.kt
package com.xrworkspace.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.xrworkspace.app.ui.XRWorkspaceApp
import com.xrworkspace.app.ui.theme.XRWorkspaceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XRWorkspaceTheme {
                XRWorkspaceApp()
            }
        }
    }
}
```

### Step 9: Create stub files for theme + root composable

Create `Theme.kt`, `Color.kt`, `Type.kt` (copy pattern from xr samples theme), and a stub `XRWorkspaceApp.kt` that shows a simple `Text("XR Workspace")`.

### Step 10: Create res files

- `res/values/strings.xml` with `app_name = "XR Workspace"`
- `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml` (copy from xr samples)

### Step 11: Build and verify

Run: `.\gradlew assembleDebug` from `xr-workspace/`

Expected: BUILD SUCCESSFUL

### Step 12: Deploy to emulator

Run: `adb install app/build/outputs/apk/debug/app-debug.apk`

Expected: App launches, shows "XR Workspace" text. If on XR emulator, enters Full Space mode.

### Step 13: Commit

```bash
git init
git add -A
git commit -m "feat: scaffold XR Workspace project with Jetpack XR dependencies"
```

---

## Task 2: Create WorkspaceViewModel and Panel State

**Files:**
- Create: `app/src/main/java/com/xrworkspace/app/viewmodel/WorkspaceViewModel.kt`

### Step 1: Create ViewModel

```kotlin
// WorkspaceViewModel.kt
package com.xrworkspace.app.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class WorkspaceUiState(
    val showDesktopPanel: Boolean = true,
    val showSpotifyPanel: Boolean = false,
    val showBrowserPanel: Boolean = false,
    val desktopStreamUrl: String = "http://192.168.1.100:8080",
    val spotifyUrl: String = "https://open.spotify.com",
)

class WorkspaceViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    fun toggleDesktopPanel() = _uiState.update { it.copy(showDesktopPanel = !it.showDesktopPanel) }
    fun toggleSpotifyPanel() = _uiState.update { it.copy(showSpotifyPanel = !it.showSpotifyPanel) }
    fun toggleBrowserPanel() = _uiState.update { it.copy(showBrowserPanel = !it.showBrowserPanel) }
    fun updateDesktopStreamUrl(url: String) = _uiState.update { it.copy(desktopStreamUrl = url) }
}
```

### Step 2: Verify it compiles

Run: `.\gradlew compileDebugKotlin`

Expected: BUILD SUCCESSFUL

### Step 3: Commit

```bash
git add -A
git commit -m "feat: add WorkspaceViewModel with panel visibility state"
```

---

## Task 3: Build Multi-Panel Spatial Layout

**Files:**
- Create: `app/src/main/java/com/xrworkspace/app/ui/SpatialWorkspace.kt`
- Modify: `app/src/main/java/com/xrworkspace/app/ui/XRWorkspaceApp.kt`

### Step 1: Create SpatialWorkspace.kt

```kotlin
// SpatialWorkspace.kt
package com.xrworkspace.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.xr.compose.spatial.ContentEdge
import androidx.xr.compose.spatial.Orbiter
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.subspace.MovePolicy
import androidx.xr.compose.subspace.ResizePolicy
import androidx.xr.compose.subspace.SpatialColumn
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.SpatialRow
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.alpha
import androidx.xr.compose.subspace.layout.height
import androidx.xr.compose.subspace.layout.width
import androidx.xr.compose.subspace.layout.padding
import com.xrworkspace.app.viewmodel.WorkspaceUiState
import kotlinx.coroutines.launch

@Composable
fun SpatialWorkspace(
    uiState: WorkspaceUiState,
    onToggleDesktop: () -> Unit,
    onToggleSpotify: () -> Unit,
    onToggleBrowser: () -> Unit,
) {
    val animatedAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch {
            animatedAlpha.animateTo(
                1.0f,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
            )
        }
    }

    Subspace {
        SpatialRow(modifier = SubspaceModifier.height(900.dp)) {
            // Main desktop streaming panel
            if (uiState.showDesktopPanel) {
                SpatialPanel(
                    SubspaceModifier
                        .alpha(animatedAlpha.value)
                        .width(1400.dp)
                        .height(900.dp)
                        .padding(end = 16.dp),
                    dragPolicy = MovePolicy(isEnabled = true),
                    resizePolicy = ResizePolicy(isEnabled = true),
                ) {
                    // Placeholder — replaced in Task 5
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Desktop Stream Panel", style = MaterialTheme.typography.headlineMedium)
                    }

                    // Orbiter toolbar attached to the main panel
                    Orbiter(
                        position = ContentEdge.Bottom,
                        offset = 48.dp,
                        alignment = Alignment.CenterHorizontally
                    ) {
                        WorkspaceToolbarPlaceholder(
                            showDesktop = uiState.showDesktopPanel,
                            showSpotify = uiState.showSpotifyPanel,
                            showBrowser = uiState.showBrowserPanel,
                            onToggleDesktop = onToggleDesktop,
                            onToggleSpotify = onToggleSpotify,
                            onToggleBrowser = onToggleBrowser,
                        )
                    }
                }
            }

            // Side panels column
            SpatialColumn {
                if (uiState.showSpotifyPanel) {
                    SpatialPanel(
                        SubspaceModifier
                            .alpha(animatedAlpha.value)
                            .width(500.dp)
                            .height(430.dp)
                            .padding(bottom = 16.dp),
                        dragPolicy = MovePolicy(isEnabled = true),
                        resizePolicy = ResizePolicy(isEnabled = true),
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Spotify Panel", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }

                if (uiState.showBrowserPanel) {
                    SpatialPanel(
                        SubspaceModifier
                            .alpha(animatedAlpha.value)
                            .width(500.dp)
                            .height(430.dp),
                        dragPolicy = MovePolicy(isEnabled = true),
                        resizePolicy = ResizePolicy(isEnabled = true),
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.tertiaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Browser Panel", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            }
        }
    }
}

// Temporary inline toolbar — replaced in Task 4
@Composable
private fun WorkspaceToolbarPlaceholder(
    showDesktop: Boolean,
    showSpotify: Boolean,
    showBrowser: Boolean,
    onToggleDesktop: () -> Unit,
    onToggleSpotify: () -> Unit,
    onToggleBrowser: () -> Unit,
) {
    androidx.compose.material3.Surface(
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 4.dp,
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.material3.FilterChip(
                selected = showDesktop,
                onClick = onToggleDesktop,
                label = { Text("Desktop") }
            )
            androidx.compose.material3.FilterChip(
                selected = showSpotify,
                onClick = onToggleSpotify,
                label = { Text("Spotify") }
            )
            androidx.compose.material3.FilterChip(
                selected = showBrowser,
                onClick = onToggleBrowser,
                label = { Text("Browser") }
            )
        }
    }
}
```

### Step 2: Update XRWorkspaceApp.kt

```kotlin
// XRWorkspaceApp.kt
package com.xrworkspace.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.xr.compose.platform.LocalSpatialCapabilities
import com.xrworkspace.app.viewmodel.WorkspaceViewModel

@Composable
fun XRWorkspaceApp(viewModel: WorkspaceViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    if (LocalSpatialCapabilities.current.isSpatialUiEnabled) {
        SpatialWorkspace(
            uiState = uiState,
            onToggleDesktop = viewModel::toggleDesktopPanel,
            onToggleSpotify = viewModel::toggleSpotifyPanel,
            onToggleBrowser = viewModel::toggleBrowserPanel,
        )
    } else {
        // Non-XR fallback
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("XR Workspace requires a spatial display.")
        }
    }
}
```

### Step 3: Build and verify

Run: `.\gradlew assembleDebug`

Expected: BUILD SUCCESSFUL

### Step 4: Deploy and verify on XR emulator

Run: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

Expected: App launches into Full Space. Shows "Desktop Stream Panel" as the main panel. Orbiter toolbar at bottom with Desktop/Spotify/Browser toggle chips. Clicking Spotify/Browser chips shows/hides side panels.

### Step 5: Commit

```bash
git add -A
git commit -m "feat: add multi-panel spatial layout with SpatialRow and Orbiter toolbar"
```

---

## Task 4: Build Proper Orbiter Toolbar with Icons

**Files:**
- Create: `app/src/main/java/com/xrworkspace/app/ui/components/WorkspaceToolbar.kt`
- Create: `app/src/main/java/com/xrworkspace/app/ui/components/SpaceControls.kt`
- Modify: `SpatialWorkspace.kt` — replace `WorkspaceToolbarPlaceholder` with real component

### Step 1: Create WorkspaceToolbar.kt

Uses Material Icons Extended (already in dependencies) for Computer, MusicNote, Language icons. FilterChip toggles for each panel. Surface with rounded shape.

### Step 2: Create SpaceControls.kt

Mirrors the xr samples `EnvironmentControls.kt` pattern: an Orbiter placed at `ContentEdge.Top` with buttons for toggling passthrough/virtual environment and requesting Home Space.

### Step 3: Update SpatialWorkspace.kt

Replace inline `WorkspaceToolbarPlaceholder` with `WorkspaceToolbar(...)`. Add `SpaceControls()` as a second Orbiter at `ContentEdge.Top`.

### Step 4: Build, deploy, verify

Expected: Proper toolbar with icons at bottom. Space controls at top with Home Space button.

### Step 5: Commit

```bash
git add -A
git commit -m "feat: add WorkspaceToolbar and SpaceControls Orbiters"
```

---

## Task 5: Desktop Streaming Panel — WebView with moonlight-web-stream

**Files:**
- Create: `app/src/main/java/com/xrworkspace/app/ui/panels/DesktopStreamPanel.kt`
- Modify: `SpatialWorkspace.kt` — replace desktop placeholder with `DesktopStreamPanel`

### Step 1: Create DesktopStreamPanel.kt

```kotlin
// DesktopStreamPanel.kt
package com.xrworkspace.app.ui.panels

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DesktopStreamPanel(streamUrl: String) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    allowContentAccess = true
                    // WebRTC requires these for moonlight-web-stream
                    setGeolocationEnabled(false)
                    databaseEnabled = true
                }
                loadUrl(streamUrl)
            }
        },
        update = { webView ->
            if (webView.url != streamUrl) {
                webView.loadUrl(streamUrl)
            }
        }
    )
}
```

### Step 2: Update SpatialWorkspace.kt

Replace the `Box(... "Desktop Stream Panel" ...)` placeholder with:
```kotlin
DesktopStreamPanel(streamUrl = uiState.desktopStreamUrl)
```

### Step 3: Build and deploy

### Step 4: Test on physical Galaxy XR

1. Ensure Sunshine + moonlight-web-stream are running on your PC
2. Set `desktopStreamUrl` to `http://<your-pc-ip>:8080`
3. Deploy to Galaxy XR
4. Expected: moonlight-web-stream's web UI appears in the main spatial panel
5. Pair with Sunshine from within the XR panel
6. Launch Desktop — your PC desktop should stream into the XR panel

**QA: Verify mouse clicks pass through the WebView to the streaming session.**

### Step 5: Commit

```bash
git add -A
git commit -m "feat: integrate moonlight-web-stream WebView for desktop streaming"
```

---

## Task 6: Spotify Panel — WebView

**Files:**
- Create: `app/src/main/java/com/xrworkspace/app/ui/panels/SpotifyPanel.kt`
- Modify: `SpatialWorkspace.kt` — replace Spotify placeholder

### Step 1: Create SpotifyPanel.kt

Same WebView pattern as DesktopStreamPanel but loading `https://open.spotify.com`. Enable cookies for session persistence. Set `mediaPlaybackRequiresUserGesture = false` for audio autoplay.

### Step 2: Update SpatialWorkspace.kt

Replace the Spotify placeholder Box with `SpotifyPanel(url = uiState.spotifyUrl)`.

### Step 3: Build, deploy to Galaxy XR

### Step 4: Test

1. Toggle Spotify panel on via toolbar
2. Expected: Spotify web player loads in the side panel
3. Log in to Spotify
4. Play a song — audio should play through the headset
5. **QA: Verify audio plays simultaneously with desktop stream audio**

### Step 5: Commit

```bash
git add -A
git commit -m "feat: add Spotify WebView panel with audio playback"
```

---

## Task 7: Browser Panel — WebView with URL Bar

**Files:**
- Create: `app/src/main/java/com/xrworkspace/app/ui/panels/BrowserPanel.kt`
- Modify: `SpatialWorkspace.kt` — replace browser placeholder

### Step 1: Create BrowserPanel.kt

WebView with a Compose-based URL bar overlay at the top. The URL bar includes:
- `TextField` for URL input (with `onImeAction` that calls `webView.loadUrl()`)
- Back / Forward / Refresh `IconButton`s
- Uses `WebViewClient.onPageFinished` to update the displayed URL

### Step 2: Update SpatialWorkspace.kt

Replace the browser placeholder Box with `BrowserPanel(initialUrl = "https://google.com")`.

### Step 3: Build, deploy

### Step 4: Test

1. Toggle Browser panel on
2. Type a URL — XR virtual keyboard should appear
3. Navigate, verify back/forward work
4. **QA: Navigate to YouTube, play a video — verify audio + video work**

### Step 5: Commit

```bash
git add -A
git commit -m "feat: add browser panel with URL bar and navigation controls"
```

---

## Task 8: Settings — Desktop Stream URL Configuration

**Files:**
- Modify: `WorkspaceViewModel.kt` — add settings persistence
- Create: `app/src/main/java/com/xrworkspace/app/ui/components/SettingsDialog.kt`

### Step 1: Add SharedPreferences for stream URL

The ViewModel reads/writes `desktopStreamUrl` from SharedPreferences so the user doesn't re-enter it every launch.

### Step 2: Create SettingsDialog.kt

A dialog (triggered from the Orbiter toolbar) with a TextField for the moonlight-web-stream URL (e.g., `http://192.168.1.100:8080`). Save button persists to SharedPreferences.

### Step 3: Test

1. Open settings, change URL, save
2. Kill and restart app
3. Expected: URL persists across sessions

### Step 4: Commit

```bash
git add -A
git commit -m "feat: add settings dialog with persistent stream URL configuration"
```

---

## Task 9: On-Device Validation and Performance Profiling

**Files:**
- No code changes — validation and bug fixes only

### Step 1: Deploy to Samsung Galaxy XR

### Step 2: Run full integration test

| Test | How to Verify | Pass Criteria |
|------|--------------|---------------|
| App launches in Full Space | Visual — app occupies full immersive space | No Home Space mode |
| Desktop stream connects | Pair with Sunshine, launch Desktop | PC desktop visible in main panel |
| Mouse input works | Click on desktop panel | Cursor moves on PC |
| Spotify plays audio | Log in, play song | Audio through headset speakers |
| Browser navigates | Enter URL, load page | Page renders correctly |
| Panels toggle | Use toolbar chips | Panels appear/disappear smoothly |
| Panels drag | Grab and move a panel | Panel repositions in 3D space |
| Panels resize | Grab panel edge | Panel resizes with content |
| Audio mixing | Desktop audio + Spotify simultaneously | Both audible, no distortion |
| 10-minute stability | Leave running | No crash, no freeze |

### Step 3: Capture performance profile

```bash
adb shell dumpsys gfxinfo com.xrworkspace.app
adb shell dumpsys meminfo com.xrworkspace.app
adb shell dumpsys thermalservice
```

Document: FPS, memory usage, thermal state.

### Step 4: Fix any bugs discovered

### Step 5: Commit

```bash
git add -A
git commit -m "fix: on-device validation bug fixes and performance tuning"
```

---

## v0.1 Completion Checklist

- [ ] App scaffolded with Jetpack XR dependencies
- [ ] Multi-panel SpatialRow layout in Full Space
- [ ] Orbiter toolbar toggles panel visibility
- [ ] Desktop streaming via moonlight-web-stream WebView
- [ ] Spotify web player in side panel
- [ ] Browser with URL bar in side panel
- [ ] Stream URL persisted in settings
- [ ] On-device validation passed on Samsung Galaxy XR
- [ ] 10-minute thermal stability confirmed
- [ ] All panels movable and resizable

---

## What Comes Next (v0.2 — Not In This Plan)

After v0.1 is validated on hardware, v0.2 upgrades the desktop panel:
- Replace WebView with `SpatialExternalSurface` + native WebRTC client (Google WebRTC SDK, BSD license)
- moonlight-web-stream provides the WebRTC stream; XR app receives it natively
- Custom input routing: XR ray-cast → panel hit test → mouse coordinate mapping → WebRTC data channel
- Target latency: 40-80ms (vs 100-200ms in v0.1)

This plan will be written separately after v0.1 gate criteria pass.
