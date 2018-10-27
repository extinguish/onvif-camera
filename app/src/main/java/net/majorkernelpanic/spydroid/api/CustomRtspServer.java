package net.majorkernelpanic.spydroid.api;

import net.majorkernelpanic.streaming.rtsp.RtspServer;

public class CustomRtspServer extends RtspServer {
    public CustomRtspServer() {
        super();
        // 这里只是没有启用用户自定义的RtspServer,对于默认的RtspServer
        // 还是启用的.
        // RTSP server disabled by default
        mEnabled = false;
    }
}

