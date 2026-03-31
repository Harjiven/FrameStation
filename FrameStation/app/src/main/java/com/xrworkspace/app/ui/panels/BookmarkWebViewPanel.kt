// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.ui.panels

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.xrworkspace.app.model.Bookmark

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BookmarkWebViewPanel(
    bookmark: Bookmark,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header bar with bookmark name and close button
        Surface(tonalElevation = 2.dp) {
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
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close ${bookmark.name}",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // WebView with DRM support and cookie persistence
        AndroidView(
            factory = { context ->
                CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                }

                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            return false
                        }
                    }

                    // Handle DRM permission requests — critical for Spotify playback
                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest?) {
                            request?.let { req ->
                                val grantedResources = req.resources.filter { resource ->
                                    resource == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID ||
                                    resource == PermissionRequest.RESOURCE_AUDIO_CAPTURE ||
                                    resource == PermissionRequest.RESOURCE_VIDEO_CAPTURE
                                }.toTypedArray()

                                if (grantedResources.isNotEmpty()) {
                                    Log.d("BookmarkWebView", "Granting DRM permissions for ${bookmark.name}: ${grantedResources.toList()}")
                                    req.grant(grantedResources)
                                } else {
                                    req.deny()
                                }
                            }
                        }
                    }

                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        @Suppress("DEPRECATION")
                        databaseEnabled = true
                        allowContentAccess = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        // Chrome user agent so sites accept the browser
                        userAgentString = "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; Samsung Galaxy XR) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                    }
                    loadUrl(bookmark.url)
                }
            },
            onRelease = { webView ->
                webView.stopLoading()
                webView.clearHistory()
                webView.destroy()
            },
            modifier = Modifier.weight(1f),
        )
    }
}
