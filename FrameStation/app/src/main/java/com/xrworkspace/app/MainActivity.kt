// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.xrworkspace.app.ui.XRWorkspaceApp
import com.xrworkspace.app.ui.theme.FrameStationTheme

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Verify Moonlight native library loads
        try {
            System.loadLibrary("moonlight-core")
            Log.i(TAG, "SUCCESS: libmoonlight-core.so loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "FAILED: libmoonlight-core.so failed to load", e)
        }

        setContent {
            FrameStationTheme {
                XRWorkspaceApp()
            }
        }
    }
}
