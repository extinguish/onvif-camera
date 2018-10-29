package net.majorkernelpanic.spydroid.api;

import net.majorkernelpanic.streaming.rtsp.RtspServer;

public class CustomRtspServer extends RtspServer {
    public CustomRtspServer() {
        super();
        // 最原始程序实现当中，默认是关闭RTSP server的，
        // 如果打开RTSP server之后，我们就可以直接通过vlc来查看rtsp视频流了
        // 我们这里将RTSP再设置为默认打开.
        mEnabled = true;
    }
}

