//
// Created by guoshichao on 18-11-30.
//
#include "include/IPCameraRtspServer.h"

/**
 * rtspPort值默认是8554
 * rtspOverHttpPort值默认是0,即不使用Rtsp Over Http tunneling
 * timeOut代表是rtsp协议的超时时间，默认为65
 * hlsSegment默认值是5
 */
IPCameraRtspServer::IPCameraRtspServer(const unsigned short rtspPort,
                                       const unsigned short rtspOverHttpPort,
                                       const int timeOut,
                                       const unsigned int hslSegment) : rtspPort(rtspPort),
                                                                        rtspOverHttpPort(
                                                                                rtspOverHttpPort),
                                                                        timeOut(timeOut),
                                                                        hslSegment(hslSegment) {
    LOGD_T(LOG_TAG, "create IPCameraRtspServer instance");
    this->scheduler = BasicTaskScheduler::createNew();
    this->env = BasicUsageEnvironment::createNew(*scheduler);
    this->rtspServer = createRtspServer();
}

IPCameraRtspServer::~IPCameraRtspServer() {
    // 我们是无法直接删除RtspServer实例的，因为rtspServer的虚构函数是protected
    // 按照live555的内部设计，对于RtspServer,TaskScheduler这样的对象，不需要我们手动回收
    // 而是通过控制整体播放的流程，然后由live555内部执行具体的回收操作.
    // 我们直接通过Medium来进行控制
    LOGD_T(LOG_TAG, "delete the instance of IPCameraRtspServer");
    stopServer();
}

int IPCameraRtspServer::addSession(const std::string &sessionName,
                                   const std::list<ServerMediaSubsession *> &subSession) {
    LOGD_T(LOG_TAG, "add session of %s", sessionName.c_str());

    int nbSubsession = 0;
    if (!subSession.empty()) {
        if (rtspServer == nullptr || rtspServer == NULL) {
            LOGERR(LOG_TAG, "the RTSP server are null!!!");
            return -1;
        }
        UsageEnvironment &env(rtspServer->envir());

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

/**
 * 创建数据源
 */
FramedSource *
IPCameraRtspServer::createFramedSource(int queueSize, bool useThread,
                                       bool repeatConfig) {
    FramedSource *source = NULL;
    // 采用h264进行编码
    source = H264_V4L2DeviceSource::createNew(*env,
                                              static_cast<unsigned int>(queueSize),
                                              useThread,
                                              repeatConfig);
    if (source == nullptr) {
        LOGERR(LOG_TAG, "Fail to create the FramedSource");
    }
    return source;
}

RTSPServer *
IPCameraRtspServer::createRtspServer() {
    Port port(rtspPort);
    LOGD_T(LOG_TAG, "the rtsp por are %d", rtspPort);
    if (env == nullptr) {
        LOGERR(LOG_TAG, "the usage environment are null");
        return nullptr;
    } else {
        LOGI_T(LOG_TAG, "the usage environment are valid");
    }
    RTSPServer *rtspServer = HTTPServer::createNew(*env, port, NULL, this->timeOut,
                                                   this->hslSegment);
    if (rtspServer != nullptr) {
        if (rtspOverHttpPort) {
            rtspServer->setUpTunnelingOverHTTP(rtspOverHttpPort);
        }
    } else {
        LOGERR(LOG_TAG, "fail to create the rtsp server");
    }
    return rtspServer;
}

/**
 * fd: 我们将编好码的数据写入到的文件的文件描述符
 */
void IPCameraRtspServer::startServer() {
    LOGD_T(LOG_TAG, "start the IPCameraRtspServer");
    // 我们创建的rtsp视频流的url地址
    std::string baseUrl = "adas_ipcamera";
    baseUrl.append("/");

    StreamReplicator *videoReplicator = NULL;

    // FIXME: 我们暂时将这个视频文件格式定义为一个虚假的值，这个值定义在videodev.h文件当中
    const int queueSize = 10;
    bool useThread = true;
    bool repeatConfig = true;

    videoSource = this->createFramedSource(queueSize, useThread,
                                           repeatConfig);

    videoReplicator = StreamReplicator::createNew(*env, videoSource, false);

    // 我们只关注单播的场景
    // 对于组播的场景我们不关注
    std::list<ServerMediaSubsession *> subSessionList;
    if (videoReplicator) {
        subSessionList.push_back(
                UnicastServerMediaSubsession::createNew(*env, videoReplicator, rtpFormat));
    }
    // FIXME: 这里的url需要重新命名
    std::string url = "unicast";
    addSession(baseUrl + url, subSessionList);
}

void IPCameraRtspServer::stopServer() {
    LOGI_T(LOG_TAG, "stop the IPCamera rtsp server");
    // FIXME: signal函数的实现有问题
    // signal(SIGINT, sigHandler);
    // this->env->taskScheduler().doEventLoop(&quit);
    Medium::close(rtspServer);
    // 回收UsageEnvironment资源
    env->reclaim();
}

long IPCameraRtspServer::getFramedSourceObjAddress() {
    if (videoSource == nullptr || videoSource == NULL) {
        LOGERR(LOG_TAG, "the FramedSource are null");
        return -1;
    }
    return reinterpret_cast<long>(videoSource);
}

//void IPCameraRtspServer::sigHandler(int signal) {
//    LOGD_T(LOG_TAG, "received signal of %d", signal);
//    quit = 1;
//}
