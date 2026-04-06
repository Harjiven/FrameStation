// SPDX-License-Identifier: GPL-3.0-or-later
package com.xrworkspace.app.streaming;

/**
 * Callbacks from a StreamService process to the UI process.
 * All methods are oneway — they don't block the service caller.
 */
oneway interface IStreamServiceClient {
    void onStageChanged(String stage);
    void onConnectionStarted();
    void onConnectionTerminated(String reason);
}
