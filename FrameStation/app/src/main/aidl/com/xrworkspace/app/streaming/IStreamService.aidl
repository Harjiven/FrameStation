// SPDX-License-Identifier: GPL-3.0-or-later
package com.xrworkspace.app.streaming;

import com.xrworkspace.app.streaming.IStreamServiceClient;

/**
 * Interface exposed by StreamService to the UI process.
 * Lets the UI process control a stream running in an isolated process.
 */
interface IStreamService {
    /** Start streaming. surface must be a valid Surface from SpatialExternalSurface. */
    void startStream(
        String serverAddress,
        in Surface surface,
        String streamSettingsJson,
        String audioSettingsJson
    );

    /** Stop the active stream. */
    void stopStream();

    /** Send mouse position. Coordinates are in stream resolution space. */
    void sendMousePosition(int x, int y, int streamWidth, int streamHeight);
    void sendMouseButtonDown(int button);
    void sendMouseButtonUp(int button);
    void sendMouseScroll(int amount);
    void sendKeyboardInput(int keyMap, int direction, int modifiers);

    /** Register callback. Call immediately after binding. */
    void registerClient(IStreamServiceClient client);
    void unregisterClient(IStreamServiceClient client);
}
