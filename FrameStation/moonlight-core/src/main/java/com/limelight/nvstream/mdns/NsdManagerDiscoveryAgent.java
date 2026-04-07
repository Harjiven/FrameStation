/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Copyright (C) 2013-2024 Moonlight Game Streaming Team
 * Copyright (C) 2026 Harjiven Dodd
 *
 * This file is part of FrameStation, which is distributed under the terms
 * of the GNU General Public License version 3 or (at your option) any later
 * version. See the COPYING file in the project root for the full license text.
 *
 * This file was extracted from moonlight-android
 * (https://github.com/moonlight-stream/moonlight-android) in 2026 and may
 * have been modified for use in FrameStation. Modifications, where present,
 * are marked inline with `// XR-REMOVED:` or `// XR-MODIFIED:` comments and
 * are summarized in FrameStation/MODIFICATIONS.md.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 */
package com.limelight.nvstream.mdns;

// XR-REMOVED: NsdManagerDiscoveryAgent requires Android NSD APIs (NsdManager)
// This stub exists to preserve the class reference. Use JmDNSDiscoveryAgent instead.
public class NsdManagerDiscoveryAgent extends MdnsDiscoveryAgent {
    public NsdManagerDiscoveryAgent(MdnsDiscoveryListener listener) {
        super(listener);
    }

    @Override
    public void startDiscovery(int discoveryIntervalMs) {
        // XR-REMOVED: NSD discovery not available without Android
    }

    @Override
    public void stopDiscovery() {
        // XR-REMOVED: NSD discovery not available without Android
    }
}
