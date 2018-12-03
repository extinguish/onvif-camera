#include "SimpleController.hpp"
#include "share_buffer_rtsp_server/include/IPCameraRtspServer.h"
#include "h264_handle/IH264DataListener.h"


void SimpleController::init(const uint16_t encode_width,
                            const uint16_t encode_height,
                            const uint32_t connect_time_out) {
    AutoMutex lock(mutex);
    this->h264DataListenerImpl = new H264DataListenerImpl();

//    RtmpHandle *rtmpHandle = new RtmpHandle();
//    int initResult = rtmpHandle->init(rtmp_url, encode_width, encode_height,
//                                      connect_time_out);

    // 将RtmpHandle关联到H264DataListenerImpl当中
//    this->h264DataListenerImpl->storeRtmpHandle(rtmpHandle);

    this->kyEncoder = new KyEncoder(h264DataListenerImpl);
    this->h264DataListenerImpl->storeKyEncoderCallback(kyEncoder);

    // 我们在这里将IPCameraRtspServer和KyEncoder结合起来

    // TODO: 结合起来有两种方式，第一种是通知KyEncoder开始向本地当中写入编好码的数据
    // TODO: 然后会产生一个文件描述符，然后就是通知DeviceSource来读取该文件当中的内容
    // TODO: 第二种就是直接feed frame给DeviceSource,但是还不确定这种方式是否会有其他的问题

    // TODO: 下面是第一种方式
    int fd = h264DataListenerImpl->getEncodedDataFd();
    // TODO: 这里可能会有问题，就是我们此时获取到的fd可能为-1,也就是说
    // TODO: 初始化有延迟.
    if (fd == -1) {
        LOGERR(LOG_TAG, "fail to get the ");
        return;
    }

    IPCameraRtspServer *ipCameraRtspServer = new IPCameraRtspServer();
    ipCameraRtspServer->startServer(fd);
    LOGD_T(LOG_TAG, "the IPCameraRtspServer has started");
}

SimpleController::~SimpleController() {
    AutoMutex lock(mutex);
    LOGD_T(TAG_C, "release the SimpleController instance");
    if (this->kyEncoder != NULL) {
        delete kyEncoder;
        this->kyEncoder = NULL;
    }
    if (this->h264DataListenerImpl != NULL) {
        delete h264DataListenerImpl;
        this->h264DataListenerImpl = NULL;
    }
}

KyEncoder *SimpleController::getKyEncoder() {
    return this->kyEncoder;
}

H264DataListenerImpl *SimpleController::getH264DataListenerImpl() {
    return this->h264DataListenerImpl;
}


