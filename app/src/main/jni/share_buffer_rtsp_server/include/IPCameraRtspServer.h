//
// Created by guoshichao on 18-11-30.
//

#ifndef IPCAMERARTSPSERVER_H
#define IPCAMERARTSPSERVER_H

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <signal.h>
#include <sys/ioctl.h>
#include <dirent.h>

#include <sstream>

// libv4l2
#include <linux/videodev2.h>

// live555
#include "../../live_server/BasicUsageEnvironment/include/BasicUsageEnvironment.hh"
#include "../../live_server/groupsock/include/GroupsockHelper.hh"
#include "../../live_server/liveMedia/include/MPEG2TransportStreamFromESSource.hh"


// project

//#include "V4l2Device.h"
//#include "V4l2Capture.h"
//#include "V4l2Output.h"

#include "H264_V4l2DeviceSource.h"
#include "ServerMediaSubsession.h"
//#include "UnicastServerMediaSubsession.h"
//#include "MulticastServerMediaSubsession.h"
//#include "SegmentServerMediaSubsession.h"
#include "HTTPServer.h"


#define LOG_TAG "ipcamera_rtsp_server"

#define v4l2_fourcc(a, b, c, d)\
    ((__u32)(a) | ((__u32)(b) << 8) | ((__u32)(c) << 16) | ((__u32)(d) << 24))

class IPCameraRtspServer {

public:
    static IPCameraRtspServer *createNew();

    std::string getVideoRtpFormat(int format, bool muxTS);

    // -----------------------------------------
    // add an RTSP session
    // -----------------------------------------
    int
    addSession(const std::string &sessionName, const std::list<ServerMediaSubsession *> &subSession);

    FramedSource *
    createFramedSource(int format, int outFd, int queueSize, bool useThread, bool repeatConfig,
                       MPEG2TransportStreamFromESSource *muxer);

    RTSPServer *createRtspServer(unsigned short rtspPort, unsigned short rtspOverHttpPort,
                                 int timeout, unsigned int hslSegment);


    // 开启服务
    void startServer();

private:
    IPCameraRtspServer(RTSPServer *rtspServer);

    RTSPServer *rtspServer;

    UsageEnvironment *env;

};


#endif //SPYDROID_IPCAMERA_IPCAMERARTSPSERVER_H
