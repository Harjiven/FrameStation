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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.HashSet;

import javax.jmdns.JmmDNS;
import javax.jmdns.NetworkTopologyDiscovery;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import javax.jmdns.impl.NetworkTopologyDiscoveryImpl;

import com.limelight.LimeLog;

public class JmDNSDiscoveryAgent extends MdnsDiscoveryAgent implements ServiceListener {
    private static final String SERVICE_TYPE = "_nvstream._tcp.local.";
    // XR-REMOVED: WifiManager.MulticastLock - not available without Android
    private Thread discoveryThread;
    private HashSet<String> pendingResolution = new HashSet<>();
    
    // The resolver factory's instance member has a static lifetime which
    // means our ref count and listener must be static also.
    private static int resolverRefCount = 0;
    private static HashSet<ServiceListener> listeners = new HashSet<>();
    private static ServiceListener nvstreamListener = new ServiceListener() {
        @Override
        public void serviceAdded(ServiceEvent event) {
            HashSet<ServiceListener> localListeners;
            
            // Copy the listener set into a new set so we can invoke
            // the callbacks without holding the listeners monitor the
            // whole time.
            synchronized (listeners) {
                localListeners = new HashSet<ServiceListener>(listeners);
            }
            
            for (ServiceListener listener : localListeners) {
                listener.serviceAdded(event);
            }
        }

        @Override
        public void serviceRemoved(ServiceEvent event) {
            HashSet<ServiceListener> localListeners;
            
            // Copy the listener set into a new set so we can invoke
            // the callbacks without holding the listeners monitor the
            // whole time.
            synchronized (listeners) {
                localListeners = new HashSet<ServiceListener>(listeners);
            }
            
            for (ServiceListener listener : localListeners) {
                listener.serviceRemoved(event);
            }
        }

        @Override
        public void serviceResolved(ServiceEvent event) {
            HashSet<ServiceListener> localListeners;
            
            // Copy the listener set into a new set so we can invoke
            // the callbacks without holding the listeners monitor the
            // whole time.
            synchronized (listeners) {
                localListeners = new HashSet<ServiceListener>(listeners);
            }
            
            for (ServiceListener listener : localListeners) {
                listener.serviceResolved(event);
            }
        }
    };

    public static class MyNetworkTopologyDiscovery extends NetworkTopologyDiscoveryImpl {
        @Override
        public boolean useInetAddress(NetworkInterface networkInterface, InetAddress interfaceAddress) {
            // This is an copy of jmDNS's implementation, except we omit the multicast check, since
            // it seems at least some devices lie about interfaces not supporting multicast when they really do.
            try {
                if (!networkInterface.isUp()) {
                    return false;
                }

                /*
                if (!networkInterface.supportsMulticast()) {
                    return false;
                }
                */

                if (networkInterface.isLoopback()) {
                    return false;
                }

                return true;
            } catch (Exception exception) {
                return false;
            }
        }
    }

    static {
        // Override jmDNS's default topology discovery class with ours
        NetworkTopologyDiscovery.Factory.setClassDelegate(new NetworkTopologyDiscovery.Factory.ClassDelegate() {
            @Override
            public NetworkTopologyDiscovery newNetworkTopologyDiscovery() {
                return new MyNetworkTopologyDiscovery();
            }
        });
    }

    private static JmmDNS referenceResolver() {
        synchronized (JmDNSDiscoveryAgent.class) {
            JmmDNS instance = JmmDNS.Factory.getInstance();
            if (++resolverRefCount == 1) {
                // This will cause the listener to be invoked for known hosts immediately.
                // JmDNS only supports one listener per service, so we have to do this here
                // with a static listener.
                instance.addServiceListener(SERVICE_TYPE, nvstreamListener);
            }
            return instance;
        }
    }

    private static void dereferenceResolver() {
        synchronized (JmDNSDiscoveryAgent.class) {
            if (--resolverRefCount == 0) {
                try {
                    JmmDNS.Factory.close();
                } catch (IOException e) {}
            }
        }
    }

    // XR-REMOVED: Context parameter removed - multicast lock not needed without Android
    public JmDNSDiscoveryAgent(MdnsDiscoveryListener listener) {
        super(listener);
    }

    private void handleResolvedServiceInfo(ServiceInfo info) {
        synchronized (pendingResolution) {
            pendingResolution.remove(info.getName());
        }

        try {
            handleServiceInfo(info);
        } catch (UnsupportedEncodingException e) {
            // Invalid DNS response
            LimeLog.info("mDNS: Invalid response for machine: "+info.getName());
            return;
        }
    }

    private void handleServiceInfo(ServiceInfo info) throws UnsupportedEncodingException {
        reportNewComputer(info.getName(), info.getPort(), info.getInet4Addresses(), info.getInet6Addresses());
    }
    
    public void startDiscovery(final int discoveryIntervalMs) {
        // Kill any existing discovery before starting a new one
        stopDiscovery();

        // XR-REMOVED: multicastLock.acquire() - not available without Android
        
        // Add our listener to the set
        synchronized (listeners) {
            listeners.add(JmDNSDiscoveryAgent.this);
        }
        
        discoveryThread = new Thread() {
            @Override
            public void run() {
                // This may result in listener callbacks so we must register
                // our listener first.
                JmmDNS resolver = referenceResolver();
                
                try {
                    while (!Thread.interrupted()) {
                        // Start an mDNS request
                        resolver.requestServiceInfo(SERVICE_TYPE, null, discoveryIntervalMs);
                        
                        // Run service resolution again for pending machines
                        ArrayList<String> pendingNames;
                        synchronized (pendingResolution) {
                            pendingNames = new ArrayList<String>(pendingResolution);
                        }
                        for (String name : pendingNames) {
                            LimeLog.info("mDNS: Retrying service resolution for machine: "+name);
                            ServiceInfo[] infos = resolver.getServiceInfos(SERVICE_TYPE, name, 500);
                            if (infos != null && infos.length != 0) {
                                LimeLog.info("mDNS: Resolved (retry) with "+infos.length+" service entries");
                                for (ServiceInfo svcinfo : infos) {
                                    handleResolvedServiceInfo(svcinfo);
                                }
                            }
                        }
                        
                        // Wait for the next polling interval
                        try {
                            Thread.sleep(discoveryIntervalMs);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }
                finally {
                    // Dereference the resolver
                    dereferenceResolver();
                }
            }
        };
        discoveryThread.setName("mDNS Discovery Thread");
        discoveryThread.start();
    }
    
    public void stopDiscovery() {
        // XR-REMOVED: multicastLock.release() - not available without Android

        // Remove our listener from the set
        synchronized (listeners) {
            listeners.remove(JmDNSDiscoveryAgent.this);
        }
        
        // If there's already a running thread, interrupt it
        if (discoveryThread != null) {
            discoveryThread.interrupt();
            discoveryThread = null;
        }
    }

    @Override
    public void serviceAdded(ServiceEvent event) {
        LimeLog.info("mDNS: Machine appeared: "+event.getInfo().getName());

        ServiceInfo info = event.getDNS().getServiceInfo(SERVICE_TYPE, event.getInfo().getName(), 500);
        if (info == null) {
            // This machine is pending resolution
            synchronized (pendingResolution) {
                pendingResolution.add(event.getInfo().getName());
            }
            return;
        }
        
        LimeLog.info("mDNS: Resolved (blocking)");
        handleResolvedServiceInfo(info);
    }

    @Override
    public void serviceRemoved(ServiceEvent event) {
        LimeLog.info("mDNS: Machine disappeared: "+event.getInfo().getName());
    }

    @Override
    public void serviceResolved(ServiceEvent event) {
        // We handle this synchronously
    }
}
