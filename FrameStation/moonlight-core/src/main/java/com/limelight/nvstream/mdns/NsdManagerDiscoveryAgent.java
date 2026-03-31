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
