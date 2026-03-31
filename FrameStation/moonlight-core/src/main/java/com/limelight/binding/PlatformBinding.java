package com.limelight.binding;

import java.io.File;

import com.limelight.binding.crypto.AndroidCryptoProvider;
import com.limelight.nvstream.http.LimelightCryptoProvider;

public class PlatformBinding {
    // XR-REMOVED: Context parameter replaced with File (data directory)
    public static LimelightCryptoProvider getCryptoProvider(File dataDir) {
        return new AndroidCryptoProvider(dataDir);
    }
}
