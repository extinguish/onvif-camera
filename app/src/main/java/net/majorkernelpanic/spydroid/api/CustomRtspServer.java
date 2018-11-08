package net.majorkernelpanic.spydroid.api;

import net.majorkernelpanic.streaming.rtsp.RtspServer;

public class CustomRtspServer extends RtspServer {
    public CustomRtspServer() {
        super();
        // 最原始程序实现当中，默认是关闭RTSP server的，
        // 如果打开RTSP server之后，我们就可以直接通过vlc来查看rtsp视频流了
        // 我们这里将RTSP再设置为默认打开.
        // 目前这里有一个很奇怪的问题，就是如果我们直接将这里的mEnabled的值设定
        // 为true的话，视频播放的帧数会刷新的特~别~~特~~~别~~~~特~~~~~~别~~~~~~~特~~~~~~~~别~~~~~~~~的~~~~~~~~~~~~~~慢
        ///////////////////////////////////////////////////////////////////////////////////////////
        // 2018-11-08
        // 问题找到了,当我们这里的mEnabled默认设置为true之后
        // 之所以视频播放的刷新率会特别低,是因为我们传输的bitrate的值特别的低,比正常情况下,直接少了10倍左右.
        // 这是因为我们传输的并不是视频流,而是音频流.
        // 而之所以如果我们将这里设置为mEnabled=true之后,推的是音频流而不是视频流,并不是因为客户请求
        // 或者其他复杂的问题,只是在SpyDroidApplication当中配置了SessionBuilder,默认推送的是audioStream
        // 而不是videoStream.所以就会出现上面提到的问题
        // 而将mEnabled设置为false之后,再设置为true的话,需要我们手动的更新一下SharedPreference,此时会涉及到
        // SessionBuilder的重新创建或者其他的复杂的逻辑(具体还没有细看),所以就没有我们遇到的那个问题
        mEnabled = true;
    }
}

