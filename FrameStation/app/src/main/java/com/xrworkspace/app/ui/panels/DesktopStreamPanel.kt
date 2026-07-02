// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.ui.panels

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView

private const val TAG = "DesktopStream"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DesktopStreamPanel(streamUrl: String) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                webViewClient = WebViewClient()
                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest?) {
                        request?.let { req ->
                            Log.d(TAG, "Permission requested: ${req.resources.toList()}")
                            req.grant(req.resources)
                        }
                    }
                }
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    allowContentAccess = true
                    @Suppress("DEPRECATION")
                    databaseEnabled = true
                }
                loadUrl(streamUrl)
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.clearHistory()
            webView.destroy()
        },
        update = { webView ->
            if (webView.url != streamUrl) {
                webView.loadUrl(streamUrl)
            }
        },
    )
}
