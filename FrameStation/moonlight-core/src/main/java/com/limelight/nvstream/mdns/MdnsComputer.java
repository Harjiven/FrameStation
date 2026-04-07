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

import java.net.Inet6Address;
import java.net.InetAddress;

public class MdnsComputer {
    private InetAddress localAddr;
    private Inet6Address v6Addr;
    private int port;
    private String name;

    public MdnsComputer(String name, InetAddress localAddress, Inet6Address v6Addr, int port) {
        this.name = name;
        this.localAddr = localAddress;
        this.v6Addr = v6Addr;
        this.port = port;
    }

    public String getName() {
        return name;
    }

    public InetAddress getLocalAddress() {
        return localAddr;
    }

    public Inet6Address getIpv6Address() {
        return v6Addr;
    }

    public int getPort() {
        return port;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof MdnsComputer) {
            MdnsComputer other = (MdnsComputer)o;

            if (!other.name.equals(name) || other.port != port) {
                return false;
            }

            if ((other.localAddr != null && localAddr == null) ||
                    (other.localAddr == null && localAddr != null) ||
                    (other.localAddr != null && !other.localAddr.equals(localAddr))) {
                return false;
            }

            if ((other.v6Addr != null && v6Addr == null) ||
                    (other.v6Addr == null && v6Addr != null) ||
                    (other.v6Addr != null && !other.v6Addr.equals(v6Addr))) {
                return false;
            }

            return true;
        }

        return false;
    }

    @Override
    public String toString() {
        return "["+name+" - "+localAddr+" - "+v6Addr+"]";
    }
}
