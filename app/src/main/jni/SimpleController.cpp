#include "SimpleController.hpp"

void SimpleController::init(const uint16_t encode_width,
                            const uint16_t encode_height,
                            const uint32_t connect_time_out) {
    AutoMutex lock(mutex);
    this->h264DataListenerImpl = new H264DataListenerImpl();
    // TODO: 我们在这里调用RtspServer来接受我们编码好的数据


//    RtmpHandle *rtmpHandle = new RtmpHandle();
//    int initResult = rtmpHandle->init(rtmp_url, encode_width, encode_height,
//                                      connect_time_out);
//    LOGD_T(TAG_C, "init rtmp handle result are %d", initResult);
    // 将RtmpHandle关联到H264DataListenerImpl当中
//    this->h264DataListenerImpl->storeRtmpHandle(rtmpHandle);

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


