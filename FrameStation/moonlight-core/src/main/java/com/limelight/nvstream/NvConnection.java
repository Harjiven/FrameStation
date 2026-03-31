package com.limelight.nvstream;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.Semaphore;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.xmlpull.v1.XmlPullParserException;

import com.limelight.LimeLog;
import com.limelight.nvstream.av.audio.AudioRenderer;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.HostHttpResponseException;
import com.limelight.nvstream.http.LimelightCryptoProvider;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.input.MouseButtonPacket;
import com.limelight.nvstream.jni.MoonBridge;

public class NvConnection {
    // Context parameters
    private LimelightCryptoProvider cryptoProvider;
    private String uniqueId;
    private ConnectionContext context;
    private static Semaphore connectionAllowed = new Semaphore(1);
    private final boolean isMonkey;

    public NvConnection(ComputerDetails.AddressTuple host, int httpsPort, String uniqueId, StreamConfiguration config, LimelightCryptoProvider cryptoProvider, X509Certificate serverCert)
    {
        this.cryptoProvider = cryptoProvider;
        this.uniqueId = uniqueId;

        this.context = new ConnectionContext();
        this.context.serverAddress = host;
        this.context.httpsPort = httpsPort;
        this.context.streamConfig = config;
        this.context.serverCert = serverCert;

        // This is unique per connection
        this.context.riKey = generateRiAesKey();
        this.context.riKeyId = generateRiKeyId();

        // XR-REMOVED: ActivityManager.isUserAMonkey() - not available without Android Context
        this.isMonkey = false;
    }
    
    private static SecretKey generateRiAesKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(128);
            return keyGen.generateKey();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
    
    private static int generateRiKeyId() {
        return new SecureRandom().nextInt();
    }

    public void stop() {
        MoonBridge.interruptConnection();
        synchronized (MoonBridge.class) {
            MoonBridge.stopConnection();
            MoonBridge.cleanupBridge();
        }
        connectionAllowed.release();
    }

    private InetAddress resolveServerAddress() throws IOException {
        InetAddress[] addrs = InetAddress.getAllByName(context.serverAddress.address);
        for (InetAddress addr : addrs) {
            try (Socket s = new Socket()) {
                s.setSoLinger(true, 0);
                s.connect(new InetSocketAddress(addr, context.serverAddress.port), 1000);
                return addr;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (addrs.length > 0) {
            return addrs[0];
        }
        else {
            throw new IOException("No addresses found for "+context.serverAddress);
        }
    }

    // XR-REMOVED: detectServerConnectionType() - requires Android ConnectivityManager
    // Replaced with stub that returns AUTO
    private int detectServerConnectionType() {
        return StreamConfiguration.STREAM_CFG_AUTO;
    }
    
    private boolean startApp() throws XmlPullParserException, IOException
    {
        NvHTTP h = new NvHTTP(context.serverAddress, context.httpsPort, uniqueId, context.serverCert, cryptoProvider);

        String serverInfo = h.getServerInfo(true);
        
        context.serverAppVersion = h.getServerVersion(serverInfo);
        if (context.serverAppVersion == null) {
            context.connListener.displayMessage("Server version malformed");
            return false;
        }

        ComputerDetails details = h.getComputerDetails(serverInfo);
        context.isNvidiaServerSoftware = details.nvidiaServer;

        context.serverGfeVersion = h.getGfeVersion(serverInfo);
                
        if (h.getPairState(serverInfo) != PairingManager.PairState.PAIRED) {
            context.connListener.displayMessage("Device not paired with computer");
            return false;
        }

        context.serverCodecModeSupport = (int)h.getServerCodecModeSupport(serverInfo);

        context.negotiatedHdr = (context.streamConfig.getSupportedVideoFormats() & MoonBridge.VIDEO_FORMAT_MASK_10BIT) != 0;
        if ((context.serverCodecModeSupport & 0x20200) == 0 && context.negotiatedHdr) {
            context.connListener.displayTransientMessage("Your PC GPU does not support streaming HDR. The stream will be SDR.");
            context.negotiatedHdr = false;
        }
        
        if ((context.streamConfig.getWidth() > 4096 || context.streamConfig.getHeight() > 4096) &&
                (h.getServerCodecModeSupport(serverInfo) & 0x200) == 0 && context.isNvidiaServerSoftware) {
            context.connListener.displayMessage("Your host PC does not support streaming at resolutions above 4K.");
            return false;
        }
        else if ((context.streamConfig.getWidth() > 4096 || context.streamConfig.getHeight() > 4096) &&
                (context.streamConfig.getSupportedVideoFormats() & ~MoonBridge.VIDEO_FORMAT_MASK_H264) == 0) {
            context.connListener.displayMessage("Your streaming device must support HEVC or AV1 to stream at resolutions above 4K.");
            return false;
        }
        else if (context.streamConfig.getHeight() >= 2160 && !h.supports4K(serverInfo)) {
            context.connListener.displayTransientMessage("You must update GeForce Experience to stream in 4K. The stream will be 1080p.");
            context.negotiatedWidth = 1920;
            context.negotiatedHeight = 1080;
        }
        else {
            context.negotiatedWidth = context.streamConfig.getWidth();
            context.negotiatedHeight = context.streamConfig.getHeight();
        }

        if (context.streamConfig.getRemote() == StreamConfiguration.STREAM_CFG_AUTO) {
            context.negotiatedRemoteStreaming = detectServerConnectionType();
            context.negotiatedPacketSize =
                    context.negotiatedRemoteStreaming == StreamConfiguration.STREAM_CFG_REMOTE ?
                            1024 : context.streamConfig.getMaxPacketSize();
        }
        else {
            context.negotiatedRemoteStreaming = context.streamConfig.getRemote();
            context.negotiatedPacketSize = context.streamConfig.getMaxPacketSize();
        }
        
        NvApp app = context.streamConfig.getApp();
        
        if (!context.streamConfig.getApp().isInitialized()) {
            LimeLog.info("Using deprecated app lookup method - Please specify an app ID in your StreamConfiguration instead");
            app = h.getAppByName(context.streamConfig.getApp().getAppName());
            if (app == null) {
                context.connListener.displayMessage("The app " + context.streamConfig.getApp().getAppName() + " is not in GFE app list");
                return false;
            }
        }
        
        if (h.getCurrentGame(serverInfo) != 0) {
            try {
                if (h.getCurrentGame(serverInfo) == app.getAppId()) {
                    if (!h.launchApp(context, "resume", app.getAppId(), context.negotiatedHdr)) {
                        context.connListener.displayMessage("Failed to resume existing session");
                        return false;
                    }
                } else {
                    return quitAndLaunch(h, context);
                }
            } catch (HostHttpResponseException e) {
                if (e.getErrorCode() == 470) {
                    context.connListener.displayMessage("This session wasn't started by this device," +
                            " so it cannot be resumed. End streaming on the original " +
                            "device or the PC itself and try again. (Error code: "+e.getErrorCode()+")");
                    return false;
                }
                else if (e.getErrorCode() == 525) {
                    context.connListener.displayMessage("The application is minimized. Resume it on the PC manually or " +
                            "quit the session and start streaming again.");
                    return false;
                } else {
                    throw e;
                }
            }
            
            LimeLog.info("Resumed existing game session");
            return true;
        }
        else {
            return launchNotRunningApp(h, context);
        }
    }

    protected boolean quitAndLaunch(NvHTTP h, ConnectionContext context) throws IOException,
            XmlPullParserException {
        try {
            if (!h.quitApp()) {
                context.connListener.displayMessage("Failed to quit previous session! You must quit it manually");
                return false;
            } 
        } catch (HostHttpResponseException e) {
            if (e.getErrorCode() == 599) {
                context.connListener.displayMessage("This session wasn't started by this device," +
                        " so it cannot be quit. End streaming on the original " +
                        "device or the PC itself. (Error code: "+e.getErrorCode()+")");
                return false;
            }
            else {
                throw e;
            }
        }

        return launchNotRunningApp(h, context);
    }
    
    private boolean launchNotRunningApp(NvHTTP h, ConnectionContext context)
            throws IOException, XmlPullParserException {
        if (!h.launchApp(context, "launch", context.streamConfig.getApp().getAppId(), context.negotiatedHdr)) {
            context.connListener.displayMessage("Failed to launch application");
            return false;
        }
        
        LimeLog.info("Launched new game session");
        
        return true;
    }

    public void start(final AudioRenderer audioRenderer, final VideoDecoderRenderer videoDecoderRenderer, final NvConnectionListener connectionListener)
    {
        new Thread(new Runnable() {
            public void run() {
                context.connListener = connectionListener;
                context.videoCapabilities = videoDecoderRenderer.getCapabilities();

                String appName = context.streamConfig.getApp().getAppName();

                context.connListener.stageStarting(appName);

                try {
                    if (!startApp()) {
                        context.connListener.stageFailed(appName, 0, 0);
                        return;
                    }
                    context.connListener.stageComplete(appName);
                } catch (HostHttpResponseException e) {
                    e.printStackTrace();
                    context.connListener.displayMessage(e.getMessage());
                    context.connListener.stageFailed(appName, 0, e.getErrorCode());
                    return;
                } catch (XmlPullParserException | IOException e) {
                    e.printStackTrace();
                    context.connListener.displayMessage(e.getMessage());
                    context.connListener.stageFailed(appName, MoonBridge.ML_PORT_FLAG_TCP_47984 | MoonBridge.ML_PORT_FLAG_TCP_47989, 0);
                    return;
                }

                ByteBuffer ib = ByteBuffer.allocate(16);
                ib.putInt(context.riKeyId);

                try {
                    connectionAllowed.acquire();
                } catch (InterruptedException e) {
                    context.connListener.displayMessage(e.getMessage());
                    context.connListener.stageFailed(appName, 0, 0);
                    return;
                }

                synchronized (MoonBridge.class) {
                    MoonBridge.setupBridge(videoDecoderRenderer, audioRenderer, connectionListener);
                    int ret = MoonBridge.startConnection(context.serverAddress.address,
                            context.serverAppVersion, context.serverGfeVersion, context.rtspSessionUrl,
                            context.serverCodecModeSupport,
                            context.negotiatedWidth, context.negotiatedHeight,
                            context.streamConfig.getRefreshRate(), context.streamConfig.getBitrate(),
                            context.negotiatedPacketSize, context.negotiatedRemoteStreaming,
                            context.streamConfig.getAudioConfiguration().toInt(),
                            context.streamConfig.getSupportedVideoFormats(),
                            context.streamConfig.getClientRefreshRateX100(),
                            context.riKey.getEncoded(), ib.array(),
                            context.videoCapabilities,
                            context.streamConfig.getColorSpace(),
                            context.streamConfig.getColorRange());
                    if (ret != 0) {
                        connectionAllowed.release();
                        return;
                    }
                }
            }
        }).start();
    }
    
    public void sendMouseMove(final short deltaX, final short deltaY) {
        if (!isMonkey) { MoonBridge.sendMouseMove(deltaX, deltaY); }
    }

    public void sendMousePosition(short x, short y, short referenceWidth, short referenceHeight) {
        if (!isMonkey) { MoonBridge.sendMousePosition(x, y, referenceWidth, referenceHeight); }
    }

    public void sendMouseMoveAsMousePosition(short deltaX, short deltaY, short referenceWidth, short referenceHeight) {
        if (!isMonkey) { MoonBridge.sendMouseMoveAsMousePosition(deltaX, deltaY, referenceWidth, referenceHeight); }
    }

    public void sendMouseButtonDown(final byte mouseButton) {
        if (!isMonkey) { MoonBridge.sendMouseButton(MouseButtonPacket.PRESS_EVENT, mouseButton); }
    }
    
    public void sendMouseButtonUp(final byte mouseButton) {
        if (!isMonkey) { MoonBridge.sendMouseButton(MouseButtonPacket.RELEASE_EVENT, mouseButton); }
    }
    
    public void sendControllerInput(final short controllerNumber,
            final short activeGamepadMask, final int buttonFlags,
            final byte leftTrigger, final byte rightTrigger,
            final short leftStickX, final short leftStickY,
            final short rightStickX, final short rightStickY) {
        if (!isMonkey) {
            MoonBridge.sendMultiControllerInput(controllerNumber, activeGamepadMask, buttonFlags,
                    leftTrigger, rightTrigger, leftStickX, leftStickY, rightStickX, rightStickY);
        }
    }

    public void sendKeyboardInput(final short keyMap, final byte keyDirection, final byte modifier, final byte flags) {
        if (!isMonkey) { MoonBridge.sendKeyboardInput(keyMap, keyDirection, modifier, flags); }
    }
    
    public void sendMouseScroll(final byte scrollClicks) {
        if (!isMonkey) { MoonBridge.sendMouseHighResScroll((short)(scrollClicks * 120)); }
    }

    public void sendMouseHScroll(final byte scrollClicks) {
        if (!isMonkey) { MoonBridge.sendMouseHighResHScroll((short)(scrollClicks * 120)); }
    }

    public void sendMouseHighResScroll(final short scrollAmount) {
        if (!isMonkey) { MoonBridge.sendMouseHighResScroll(scrollAmount); }
    }

    public void sendMouseHighResHScroll(final short scrollAmount) {
        if (!isMonkey) { MoonBridge.sendMouseHighResHScroll(scrollAmount); }
    }

    public int sendTouchEvent(byte eventType, int pointerId, float x, float y, float pressureOrDistance,
                              float contactAreaMajor, float contactAreaMinor, short rotation) {
        if (!isMonkey) {
            return MoonBridge.sendTouchEvent(eventType, pointerId, x, y, pressureOrDistance,
                    contactAreaMajor, contactAreaMinor, rotation);
        } else { return MoonBridge.LI_ERR_UNSUPPORTED; }
    }

    public int sendPenEvent(byte eventType, byte toolType, byte penButtons, float x, float y,
                            float pressureOrDistance, float contactAreaMajor, float contactAreaMinor,
                            short rotation, byte tilt) {
        if (!isMonkey) {
            return MoonBridge.sendPenEvent(eventType, toolType, penButtons, x, y, pressureOrDistance,
                    contactAreaMajor, contactAreaMinor, rotation, tilt);
        } else { return MoonBridge.LI_ERR_UNSUPPORTED; }
    }

    public int sendControllerArrivalEvent(byte controllerNumber, short activeGamepadMask, byte type,
                                          int supportedButtonFlags, short capabilities) {
        return MoonBridge.sendControllerArrivalEvent(controllerNumber, activeGamepadMask, type, supportedButtonFlags, capabilities);
    }

    public int sendControllerTouchEvent(byte controllerNumber, byte eventType, int pointerId,
                                        float x, float y, float pressure) {
        if (!isMonkey) {
            return MoonBridge.sendControllerTouchEvent(controllerNumber, eventType, pointerId, x, y, pressure);
        } else { return MoonBridge.LI_ERR_UNSUPPORTED; }
    }

    public int sendControllerMotionEvent(byte controllerNumber, byte motionType,
                                         float x, float y, float z) {
        if (!isMonkey) {
            return MoonBridge.sendControllerMotionEvent(controllerNumber, motionType, x, y, z);
        } else { return MoonBridge.LI_ERR_UNSUPPORTED; }
    }

    public void sendControllerBatteryEvent(byte controllerNumber, byte batteryState, byte batteryPercentage) {
        MoonBridge.sendControllerBatteryEvent(controllerNumber, batteryState, batteryPercentage);
    }

    public void sendUtf8Text(final String text) {
        if (!isMonkey) { MoonBridge.sendUtf8Text(text); }
    }

    public static String findExternalAddressForMdns(String stunHostname, int stunPort) {
        return MoonBridge.findExternalAddressIP4(stunHostname, stunPort);
    }
}
