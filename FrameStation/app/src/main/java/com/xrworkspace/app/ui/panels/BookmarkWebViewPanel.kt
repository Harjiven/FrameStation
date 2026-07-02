// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.ui.panels

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.xrworkspace.app.model.Bookmark

private const val TAG = "BookmarkWebView"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BookmarkWebViewPanel(
    bookmark: Bookmark,
    onClose: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // Audio focus for spatial audio anchoring
    val audioFocusRequest = remember {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener {}
            .build()
    }

    // AudioManager is process-wide; cache the lookup so we don't hit getSystemService
    // and the cast every time bookmark.id changes.
    val audioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as? AudioManager
    }
    DisposableEffect(bookmark.id) {
        audioManager?.requestAudioFocus(audioFocusRequest)
        onDispose { audioManager?.abandonAudioFocusRequest(audioFocusRequest) }
    }

    // URL bar state — only used for ephemeral tabs
    var currentUrl by remember(bookmark.id) { mutableStateOf(bookmark.url.takeIf { it != "about:blank" } ?: "") }
    var urlBarText by remember(bookmark.id) { mutableStateOf(currentUrl) }
    var urlBarFocused by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    // Hold a reference to the WebView so URL bar actions can drive it
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Header bar ────────────────────────────────────────────────────────
        Surface(tonalElevation = 2.dp) {
            if (bookmark.isEphemeral) {
                // Ephemeral tab: full URL bar with back, refresh, address field, close
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Back
                    IconButton(
                        onClick = { webViewRef?.goBack() },
                        enabled = canGoBack,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    // Refresh
                    IconButton(
                        onClick = { webViewRef?.reload() },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    // URL field
                    OutlinedTextField(
                        value = if (urlBarFocused) urlBarText else currentUrl,
                        onValueChange = { urlBarText = it },
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { focus ->
                                urlBarFocused = focus.isFocused
                                if (focus.isFocused) urlBarText = currentUrl
                            },
                        singleLine = true,
                        placeholder = { Text("Enter URL or search…", style = MaterialTheme.typography.bodySmall) },
                        textStyle = MaterialTheme.typography.bodySmall,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go,
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                val nav = normalizeUrl(urlBarText)
                                currentUrl = nav
                                webViewRef?.loadUrl(nav)
                            }
                        ),
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Close
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            } else {
                // Saved bookmark: simple name + close button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = bookmark.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close ${bookmark.name}",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        // ── WebView ───────────────────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                CookieManager.getInstance().setAcceptCookie(true)

                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )

                    // Hardware acceleration required for <video> compositing
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = false

                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            if (bookmark.isEphemeral && url != null && url != "about:blank") {
                                currentUrl = url
                                if (!urlBarFocused) urlBarText = url
                            }
                            canGoBack = view?.canGoBack() ?: false
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (bookmark.isEphemeral && url != null && url != "about:blank") {
                                currentUrl = url
                                if (!urlBarFocused) urlBarText = url
                            }
                            canGoBack = view?.canGoBack() ?: false
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest?) {
                            request?.let { req ->
                                val granted = req.resources.filter { r ->
                                    r == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID ||
                                    r == PermissionRequest.RESOURCE_AUDIO_CAPTURE ||
                                    r == PermissionRequest.RESOURCE_VIDEO_CAPTURE
                                }.toTypedArray()
                                if (granted.isNotEmpty()) {
                                    Log.d(TAG, "Granting DRM/media permissions: ${granted.toList()}")
                                    req.grant(granted)
                                } else {
                                    req.deny()
                                }
                            }
                        }

                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            super.onReceivedTitle(view, title)
                            // Ephemeral tab title shown in URL field placeholder — no action needed
                        }
                    }

                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        // COMPATIBILITY_MODE (not ALWAYS_ALLOW): keeps mixed-content sites like
                        // Plex working while still blocking active mixed content on HTTPS pages —
                        // ALWAYS_ALLOW is laxer than any real browser and draws Play security review.
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        @Suppress("DEPRECATION")
                        databaseEnabled = true
                        allowContentAccess = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        userAgentString = if (bookmark.useDesktopUa) {
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                        } else {
                            "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; Samsung Galaxy XR) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                        }
                    }

                    // Load initial URL — blank page for new tabs (user types URL in bar)
                    val initialUrl = bookmark.url.takeIf { it != "about:blank" }
                    if (initialUrl != null) loadUrl(initialUrl)

                    webViewRef = this
                }
            },
            update = { webView ->
                // Keep the ref fresh across recompositions
                webViewRef = webView
            },
            onRelease = { webView ->
                webViewRef = null
                webView.stopLoading()
                webView.clearHistory()
                webView.destroy()
            },
            modifier = Modifier.weight(1f),
        )
    }
}

/** Ensure a URL has a scheme; treat bare strings as HTTPS or a Google search. */
private fun normalizeUrl(input: String): String {
    val trimmed = input.trim()
    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
        else -> "https://www.google.com/search?q=${android.net.Uri.encode(trimmed)}"
    }
}
