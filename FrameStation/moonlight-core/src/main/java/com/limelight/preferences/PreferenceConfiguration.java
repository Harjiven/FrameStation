package com.limelight.preferences;

import com.limelight.nvstream.jni.MoonBridge;

// XR-REMOVED: All SharedPreferences/Context-dependent methods removed
// Only data fields, enums, and static utility methods retained
public class PreferenceConfiguration {
    public enum FormatOption {
        AUTO,
        FORCE_AV1,
        FORCE_HEVC,
        FORCE_H264,
    }

    public enum AnalogStickForScrolling {
        NONE,
        RIGHT,
        LEFT
    }

    public static final int FRAME_PACING_MIN_LATENCY = 0;
    public static final int FRAME_PACING_BALANCED = 1;
    public static final int FRAME_PACING_CAP_FPS = 2;
    public static final int FRAME_PACING_MAX_SMOOTHNESS = 3;

    public static final String RES_360P = "640x360";
    public static final String RES_480P = "854x480";
    public static final String RES_720P = "1280x720";
    public static final String RES_1080P = "1920x1080";
    public static final String RES_1440P = "2560x1440";
    public static final String RES_4K = "3840x2160";
    public static final String RES_NATIVE = "Native";

    public int width, height, fps;
    public int bitrate;
    public FormatOption videoFormat;
    public int deadzonePercentage;
    public int oscOpacity;
    public boolean stretchVideo, enableSops, playHostAudio, disableWarnings;
    public String language;
    public boolean smallIconMode, multiController, usbDriver, flipFaceButtons;
    public boolean onscreenController;
    public boolean onlyL3R3;
    public boolean showGuideButton;
    public boolean enableHdr;
    public boolean enablePip;
    public boolean enablePerfOverlay;
    public boolean enableLatencyToast;
    public boolean bindAllUsb;
    public boolean mouseEmulation;
    public AnalogStickForScrolling analogStickForScrolling;
    public boolean mouseNavButtons;
    public boolean unlockFps;
    public boolean vibrateOsc;
    public boolean vibrateFallbackToDevice;
    public int vibrateFallbackToDeviceStrength;
    public boolean touchscreenTrackpad;
    public MoonBridge.AudioConfiguration audioConfiguration;
    public int framePacing;
    public boolean absoluteMouseMode;
    public boolean enableAudioFx;
    public boolean reduceRefreshRate;
    public boolean fullRange;
    public boolean gamepadMotionSensors;
    public boolean gamepadTouchpadAsMouse;
    public boolean gamepadMotionSensorsFallbackToDevice;

    public static boolean isNativeResolution(int width, int height) {
        if (width == 640 && height == 360) return false;
        else if (width == 854 && height == 480) return false;
        else if (width == 1280 && height == 720) return false;
        else if (width == 1920 && height == 1080) return false;
        else if (width == 2560 && height == 1440) return false;
        else if (width == 3840 && height == 2160) return false;
        return true;
    }

    public static boolean isSquarishScreen(int width, int height) {
        float longDim = Math.max(width, height);
        float shortDim = Math.min(width, height);
        return longDim / shortDim < 1.3f;
    }

    public static int getDefaultBitrate(String resString, String fpsString) {
        int width = Integer.parseInt(resString.split("x")[0]);
        int height = Integer.parseInt(resString.split("x")[1]);
        int fps = Integer.parseInt(fpsString);

        double frameRateFactor = (fps <= 60 ? fps : (Math.sqrt(fps / 60.f) * 60.f)) / 30.f;

        int[] pixelVals = { 640 * 360, 854 * 480, 1280 * 720, 1920 * 1080, 2560 * 1440, 3840 * 2160, -1 };
        int[] factorVals = { 1, 2, 5, 10, 20, 40, -1 };

        float resolutionFactor;
        int pixels = width * height;
        for (int i = 0; ; i++) {
            if (pixels == pixelVals[i]) {
                resolutionFactor = factorVals[i];
                break;
            } else if (pixels < pixelVals[i]) {
                if (i == 0) {
                    resolutionFactor = factorVals[i];
                } else {
                    resolutionFactor = ((float)(pixels - pixelVals[i-1]) / (pixelVals[i] - pixelVals[i-1])) * (factorVals[i] - factorVals[i-1]) + factorVals[i-1];
                }
                break;
            } else if (pixelVals[i] == -1) {
                resolutionFactor = factorVals[i-1];
                break;
            }
        }

        return (int)Math.round(resolutionFactor * frameRateFactor) * 1000;
    }

    // XR-REMOVED: readPreferences(Context), getDefaultSmallMode(Context), resetStreamingSettings(Context),
    // completeLanguagePreferenceMigration(Context), getVideoFormatValue(Context), getFramePacingValue(Context),
    // getAnalogStickForScrollingValue(Context), getDefaultBitrate(Context), isSquarishScreen(Display),
    // isShieldAtvFirmwareWithBrokenHdr() - all require Android Context/SharedPreferences/Display
}
