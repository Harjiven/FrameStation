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
package com.limelight.nvstream.av;

public class ByteBufferDescriptor {
    public byte[] data;
    public int offset;
    public int length;
    
    public ByteBufferDescriptor nextDescriptor;
    
    public ByteBufferDescriptor(byte[] data, int offset, int length)
    {
        this.data = data;
        this.offset = offset;
        this.length = length;
    }
    
    public ByteBufferDescriptor(ByteBufferDescriptor desc)
    {
        this.data = desc.data;
        this.offset = desc.offset;
        this.length = desc.length;
    }
    
    public void reinitialize(byte[] data, int offset, int length)
    {
        this.data = data;
        this.offset = offset;
        this.length = length;
        this.nextDescriptor = null;
    }
    
    public void print()
    {
        print(offset, length);
    }
    
    public void print(int length)
    {
        print(this.offset, length);
    }
    
    public void print(int offset, int length)
    {
        for (int i = offset; i < offset+length;) {
            if (i + 8 <= offset+length) {
                System.out.printf("%x: %02x %02x %02x %02x %02x %02x %02x %02x\n", i,
                        data[i], data[i+1], data[i+2], data[i+3], data[i+4], data[i+5], data[i+6], data[i+7]);
                i += 8;
            }
            else {
                System.out.printf("%x: %02x \n", i, data[i]);
                i++;
            }
        }
        System.out.println();
    }
}
