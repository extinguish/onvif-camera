/* ---------------------------------------------------------------------------
** This software is in the public domain, furnished "as is", without technical
** support, and with no warranty, express or implied, as to its usefulness for
** any purpose.
**
** ServerMediaSubsession.cpp
** 
** -------------------------------------------------------------------------*/

#include <sstream>
#include <linux/videodev2.h>

// #include "../live_server/liveMedia/include/MJPEGVideoSource.hh"
#include "../live_server/liveMedia/include/H264VideoStreamDiscreteFramer.hh"
#include "../live_server/liveMedia/include/MPEG2TransportStreamFramer.hh"
#include "../live_server/liveMedia/include/H265VideoStreamDiscreteFramer.hh"
#include "../live_server/liveMedia/include/H265VideoRTPSink.hh"
#include "../live_server/liveMedia/include/H264VideoRTPSink.hh"
#include "../live_server/liveMedia/include/VP8VideoRTPSink.hh"
#include "../live_server/liveMedia/include/VP9VideoRTPSink.hh"
#include "../live_server/liveMedia/include/JPEGVideoRTPSink.hh"

// project
#include "../live_server/liveMedia/include/ServerMediaSession.hh"
#include "../live_server/liveMedia/include/SimpleRTPSink.hh"
#include "../live_server/liveMedia/include/RawVideoRTPSink.hh"
#include "include/ServerMediaSubsession.h"
#include "include/DeviceSource.h"

/// 根据用户指定的视频格式-format来创建对应的FramedSource
// ---------------------------------
//   BaseServerMediaSubsession
// ---------------------------------
FramedSource *
BaseServerMediaSubsession::createSource(UsageEnvironment &env, FramedSource *videoES,
                                        const std::string &format) {
    FramedSource *source = NULL;
    if (format == "video/MP2T") {
        source = MPEG2TransportStreamFramer::createNew(env, videoES);
    } else if (format == "video/H264") {
        source = H264VideoStreamDiscreteFramer::createNew(env, videoES);
    }
#if LIVEMEDIA_LIBRARY_VERSION_INT > 1414454400
    else if (format == "video/H265") {
        source = H265VideoStreamDiscreteFramer::createNew(env, videoES);
    }
#endif
    else {
        source = videoES;
    }
    return source;
}

/// 根据live555的版本不同，处理的能力也不同
/// 当前我们正在使用的live555的版本是1539734400
RTPSink *BaseServerMediaSubsession::createSink(UsageEnvironment &env, Groupsock *rtpGroupsock,
                                               unsigned char rtpPayloadTypeIfDynamic,
                                               const std::string &format,
                                               V4L2DeviceSource *source) {
    // TODO: guoshichao 理论上，我们需要的只是h264视频格式的处理，剩下的不需要处理
    RTPSink *videoSink = NULL;
    if (format == "video/MP2T") {
        videoSink = SimpleRTPSink::createNew(env, rtpGroupsock, rtpPayloadTypeIfDynamic, 90000,
                                             "video", "MP2T", 1,
                                             True, False);
    } else if (format == "video/H264") {
        videoSink = H264VideoRTPSink::createNew(env, rtpGroupsock, rtpPayloadTypeIfDynamic);
    } else if (format == "video/VP8") {
        videoSink = VP8VideoRTPSink::createNew(env, rtpGroupsock, rtpPayloadTypeIfDynamic);
    }
#if LIVEMEDIA_LIBRARY_VERSION_INT > 1414454400
    else if (format == "video/VP9") {
        videoSink = VP9VideoRTPSink::createNew(env, rtpGroupsock, rtpPayloadTypeIfDynamic);
    } else if (format == "video/H265") {
        videoSink = H265VideoRTPSink::createNew(env, rtpGroupsock, rtpPayloadTypeIfDynamic);
    }
#endif
    else if (format == "video/JPEG") {
        videoSink = JPEGVideoRTPSink::createNew(env, rtpGroupsock);
    }
#if LIVEMEDIA_LIBRARY_VERSION_INT >= 1536192000
    else if (format == "video/RAW") { // 直接读取video/RAW格式的视频数据，是在高版本当中的live555当中才支持的
        std::string sampling;
        // FIXME: guoshichao 我们将下面的sampling值直接写死了，当然我们可能都不需要处理video/RAW
        sampling = "YCbCr-4:4:4";
//        switch (source->getCaptureFormat()) {
//            case V4L2_PIX_FMT_YUV444:
//                sampling = "YCbCr-4:4:4";
//                break;
//            case V4L2_PIX_FMT_YUYV:
//                sampling = "YCbCr-4:2:2";
//                break;
//        }
        // FIXME: guoshichao 以下是原始的实现,稍后解开注释然后进行修复
//        int videoWidth = source->getWidth();
//        int videoHeight = source->getHeight();
        int videoWidth = 320;
        int videoHeight = 480;
        videoSink = RawVideoRTPSink::createNew(env, rtpGroupsock, rtpPayloadTypeIfDynamic,
                                               videoHeight,
                                               videoWidth, 8, sampling.c_str());


        if (videoSink) {
            source->setAuxLine(videoSink->auxSDPLine());
        }
    }
#endif
    else if (format.find("audio/L16") == 0) {
        std::istringstream is(format);
        std::string dummy;
        getline(is, dummy, '/');
        getline(is, dummy, '/');
        std::string sampleRate("44100");
        getline(is, sampleRate, '/');
        std::string channels("2");
        getline(is, channels);
        videoSink = SimpleRTPSink::createNew(env, rtpGroupsock, rtpPayloadTypeIfDynamic,
                                             std::stoi(sampleRate), "audio",
                                             "L16", std::stoi(channels), True, False);
    }
    return videoSink;
}

char const *
BaseServerMediaSubsession::getAuxLine(V4L2DeviceSource *source, unsigned char rtpPayloadType) {
    const char *auxLine = NULL;
    if (source) {
        std::ostringstream os;
        os << "a=fmtp:" << int(rtpPayloadType) << " ";
        os << source->getAuxLine();
        os << "\r\n";
        // FIXME: guoshichao: 稍后解开注释，进行修复
//        int width = source->getWidth();
//        int height = source->getHeight();
        // FIXME: guoshichao: 以下的高度和宽度是随便指定的，稍后修改
        int width = 320;
        int height = 480;

        if ((width > 0) && (height > 0)) {
            os << "a=x-dimensions:" << width << "," << height << "\r\n";
        }
        auxLine = strdup(os.str().c_str());
    }
    return auxLine;
}

