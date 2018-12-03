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
#include "H264_V4l2DeviceSource.h"
#include "ServerMediaSubsession.h"
#include "UnicastServerMediaSubsession.h"
#include "HTTPServer.h"


#define LOG_TAG "ipcamera_rtsp_server"

class IPCameraRtspServer {

public:
    /**
     * hlsSegment的默认值为0
     * 当hlsSegment的值>0时，我们使用HLS协议来作为rtsp底层的数据传输通道
     * 目前我们不需要HLS协议
     */
    IPCameraRtspServer(const unsigned short rtspPort = 8554,
                       const unsigned short rtspOverHttpPort = 0,
                       const int timeOut = 65,
                       const unsigned int hlsSegment = 0);

    ~IPCameraRtspServer();

    // -----------------------------------------
    // add an RTSP session
    // -----------------------------------------
    int
    addSession(const std::string &sessionName,
               const std::list<ServerMediaSubsession *> &subSession);

    FramedSource *
    createFramedSource(int queueSize, bool useThread, bool repeatConfig, int fd);

    RTSPServer *createRtspServer();

    // 开启rtsp服务
    void startServer(int fd);

    // 停止rtsp服务
    void stopServer();

private:
    TaskScheduler *scheduler;

    RTSPServer *rtspServer;

    UsageEnvironment *env;

    // 对于AdasIPCamera,我们从ShareBuffer当中读取出视频数据之后，直接就编码成h264格式，这是固定的
    const std::string rtpFormat = "video/H264";

    const unsigned short rtspPort;
    const unsigned short rtspOverHttpPort;
    const unsigned int timeOut;
    const unsigned int hslSegment;

    // FIXME: guoshichao 这里的实现有问题
//    static int quit;
//
//    static void sigHandler(int signal);
};


#endif //SPYDROID_IPCAMERA_IPCAMERARTSPSERVER_H
