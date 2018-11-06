package net.majorkernelpanic.spydroid.api;

import net.majorkernelpanic.streaming.rtsp.RtspServer;

public class CustomRtspServer extends RtspServer {
    public CustomRtspServer() {
        super();
        // 最原始程序实现当中，默认是关闭RTSP server的，
        // 如果打开RTSP server之后，我们就可以直接通过vlc来查看rtsp视频流了
        // 我们这里将RTSP再设置为默认打开.
        // TODO: 目前这里有一个很奇怪的问题，就是如果我们直接将这里的mEnabled的值设定
        // TODO: 为true的话，视频播放的帧数会刷新的特~别~~特~~~别~~~~特~~~~~~别~~~~~~~特~~~~~~~~别~~~~~~~~的~~~~~~~~~~~~~~慢
        // TODO: 具体的原因还有待排查
        mEnabled = false;
    }
}

