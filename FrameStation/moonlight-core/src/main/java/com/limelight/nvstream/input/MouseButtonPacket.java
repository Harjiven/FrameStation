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
package com.limelight.nvstream.input;

public class MouseButtonPacket {
    public static final byte PRESS_EVENT = 0x07;
    public static final byte RELEASE_EVENT = 0x08;
    
    public static final byte BUTTON_LEFT = 0x01;
    public static final byte BUTTON_MIDDLE = 0x02;
    public static final byte BUTTON_RIGHT = 0x03;
    public static final byte BUTTON_X1 = 0x04;
    public static final byte BUTTON_X2 = 0x05;
}
