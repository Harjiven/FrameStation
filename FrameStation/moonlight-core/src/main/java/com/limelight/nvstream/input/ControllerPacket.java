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

public class ControllerPacket {
    public static final int A_FLAG = 0x1000;
    public static final int B_FLAG = 0x2000;
    public static final int X_FLAG = 0x4000;
    public static final int Y_FLAG = 0x8000;
    public static final int UP_FLAG = 0x0001;
    public static final int DOWN_FLAG = 0x0002;
    public static final int LEFT_FLAG = 0x0004;
    public static final int RIGHT_FLAG = 0x0008;
    public static final int LB_FLAG = 0x0100;
    public static final int RB_FLAG = 0x0200;
    public static final int PLAY_FLAG = 0x0010;
    public static final int BACK_FLAG = 0x0020;
    public static final int LS_CLK_FLAG = 0x0040;
    public static final int RS_CLK_FLAG = 0x0080;
    public static final int SPECIAL_BUTTON_FLAG = 0x0400;

    // Extended buttons (Sunshine only)
    public static final int PADDLE1_FLAG  = 0x010000;
    public static final int PADDLE2_FLAG  = 0x020000;
    public static final int PADDLE3_FLAG  = 0x040000;
    public static final int PADDLE4_FLAG  = 0x080000;
    public static final int TOUCHPAD_FLAG = 0x100000; // Touchpad buttons on Sony controllers
    public static final int MISC_FLAG     = 0x200000; // Share/Mic/Capture/Mute buttons on various controllers
}
