//
// Created by guoshichao on 18-11-30.
//
#include "include/IPCameraRtspServer.h"


int IPCameraRtspServer::addSession(const std::string &sessionName,
                                   const std::list<ServerMediaSubsession *> &subSession) {
    int nbSubsession = 0;
    if (!subSession.empty()) {
        UsageEnvironment &env(this->rtspServer->envir());
        ServerMediaSession *sms = ServerMediaSession::createNew(env, sessionName.c_str());
        if (sms != NULL) {
            for (std::list<ServerMediaSubsession *>::const_iterator subIt = subSession.begin();
                    subIt != subSession.end(); ++subIt) {
                sms->addSubsession(*subIt);
                nbSubsession++;
            }

            rtspServer->addServerMediaSession(sms);

            char *url = rtspServer->rtspURL(sms);
            if (url != NULL) {
                LOGD_T(LOG_TAG, "Play this stream using the URL Of %s", url);
                delete[] url;
            }
        }
    }
    return nbSubsession;
}


// TODO: 我们应该不需要这个方法
std::string IPCameraRtspServer::getVideoRtpFormat(int format, bool muxTS) {
    std::string rtpFormat;
    if (muxTS) {
        rtpFormat = "video/MP2T";
    } else {
        switch (format) {
            case V4L2_PIX_FMT_HEVC :
                rtpFormat = "video/H265";
                break;
            case V4L2_PIX_FMT_H264 :
                rtpFormat = "video/H264";
                break;
            case V4L2_PIX_FMT_MJPEG:
                rtpFormat = "video/JPEG";
                break;
            case V4L2_PIX_FMT_JPEG :
                rtpFormat = "video/JPEG";
                break;
            case V4L2_PIX_FMT_VP8  :
                rtpFormat = "video/VP8";
                break;
            case V4L2_PIX_FMT_VP9  :
                rtpFormat = "video/VP9";
                break;
            case V4L2_PIX_FMT_YUYV :
                rtpFormat = "video/RAW";
                break;
        }
    }

    return rtpFormat;
}

FramedSource *
IPCameraRtspServer::createFramedSource(int format, int outFd, int queueSize, bool useThread,
                                       bool repeatConfig, MPEG2TransportStreamFromESSource *muxer) {

    // guoshichao: 这里需要确定我们最终推送出去的数据流是否需要进行封装，如果需要封装，那么我们这里就要提供
    // muxer,如果不需要封装，即用户需要的就是原始的h264视频流的话，那么就不用提供封装了
    bool muxTS = (muxer != NULL);
    FramedSource *source = NULL;
    // V4L2_PIX_FMT_H264和V4L2_PIX_FMT_HEVC本身是由videodev.h直接定义提供的
    if (format == V4L2_PIX_FMT_H264) {
        // 采用h264进行编码
        source = H264_V4L2DeviceSource::createNew(*env,
                                                  outFd,
                                                  queueSize,
                                                  useThread,
                                                  repeatConfig,
                                                  muxTS);
        if (muxTS) {
            muxer->addNewVideoSource(source, 5);
            source = muxer;
        }
    } else if (!muxTS) {
        source = V4L2DeviceSource::createNew(*env, outFd, queueSize, useThread);
    } else {
        LOGD_T(LOG_TAG, "TS in nor compatible with format");
    }
    return source;
}

RTSPServer *
IPCameraRtspServer::createRtspServer(unsigned short rtspPort, unsigned short rtspOverHttpPort,
                                     int timeout, unsigned int hslSegment) {
    RTSPServer *rtspServer = HTTPServer::createNew(*env, rtspPort, NULL, timeout, hslSegment);
    if (rtspServer != NULL) {
        if (rtspOverHttpPort) {
            rtspServer->setUpTunnelingOverHTTP(rtspOverHttpPort);
        }
    }
    return rtspServer;
}

void IPCameraRtspServer::startServer() {
    LOGD_T(LOG_TAG, "start the IPCameraRtspServer");



}
