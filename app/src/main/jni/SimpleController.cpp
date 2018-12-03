#include "SimpleController.hpp"
#include "share_buffer_rtsp_server/include/IPCameraRtspServer.h"
#include "h264_handle/IH264DataListener.h"

/**
 * TODO: 理论上，这里的encode_width, encode_height和connect_time_out的值需要传递给RtspServer
 */
void SimpleController::init(const uint16_t encode_width,
                            const uint16_t encode_height,
                            const uint32_t connect_time_out) {
    AutoMutex lock(mutex);
    this->h264DataListenerImpl = new H264DataListenerImpl();

    // 其实rtsp对于我们来说，本质上来说，同rtmp是完全一样的，只是具体的传输协议协议细节有些区别
    // 所以我们这里直接按照之前的rtmp的方式来处理就好了
    IPCameraRtspServer *ipCameraRtspServer = new IPCameraRtspServer();
    ipCameraRtspServer->startServer();
    LOGD_T(LOG_TAG, "the IPCameraRtspServer has started");

    // 将IPCameraRtspServer关联到H264DataListenerImpl当中
    this->h264DataListenerImpl->storeIPCameraRtspServerObjAddress(ipCameraRtspServer);

    this->kyEncoder = new KyEncoder(h264DataListenerImpl);
    this->h264DataListenerImpl->storeKyEncoderCallback(kyEncoder);
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


