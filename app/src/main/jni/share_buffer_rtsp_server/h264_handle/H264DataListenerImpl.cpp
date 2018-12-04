#include <pthread.h>
#include "include/IH264DataListener.h"
#include "include/AdasUtil.h"
#include <stdio.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/uio.h>

#define TAG_H "h264_handler"

H264DataListenerImpl::H264DataListenerImpl() {
}

// 在原始rtmp的实现当中，这里的start()方法是用于开始推流的操作，即从encoded_frame_queue
// 当中读取数据
// 但是对于rtsp server来说，是在收到用户的请求之后，才需要开始从encoded_frame_queue当中
// 拉取数据流
// 所以h264_handle从另一个角度来说，也需要被IPCameraRtspServer来控制
void H264DataListenerImpl::start() {
    LOGD_T(TAG_H, "start the H264 encoded thread");
    pthread_create(&send_thread_id, NULL, H264DataListenerImpl::send, this);
}

void *H264DataListenerImpl::send(void *data_callback) {
    LOGD_T(TAG_H, "start the send thread");
    H264DataListenerImpl *data_listener = reinterpret_cast<H264DataListenerImpl *>(data_callback);
    data_listener->send_video_frame();
    pthread_exit(NULL);
}

void *H264DataListenerImpl::send_video_frame() {
    encoding = true;
    if (!setupNegotiate) {
        LOGD_T(TAG_H, "we need to setup the rtsp server");
        long videoSourceObjAddress = rtspServer->getFramedSourceObjAddress();
        if (videoSourceObjAddress == -1) {
            LOGERR(LOG_TAG, "the FramedSource instance address are invalid");
            return 0;
        }
        FramedSource *videoSource = reinterpret_cast<FramedSource *>(videoSourceObjAddress);
        if (videoSource == nullptr) {
            LOGERR(LOG_TAG, "fail to get the FramedSource instance");
            return 0;
        }
        V4L2DeviceSource *encapsulatedVideoSource = static_cast<V4L2DeviceSource *>(videoSource);
        encapsulatedVideoSource->setupRawH264FrameQueue(
                reinterpret_cast<long>(encoded_frame_queue));
        setupNegotiate = true;
    }
    return 0;
}

/**
 * 我们需要识别出数据当中的pps和sps，以及普通视频帧,
 * 然后将这些数据帧传递给rtsp_server
 *
 * @param data h264 data
 * @param flags 2:encode flag, 9:keyframe, 8:normal frame
 * @param offset
 * @param size
 */
void
H264DataListenerImpl::onVideoFrame(uint8_t *data, uint32_t flags, int32_t offset,
                                   const int32_t size, int64_t time_stamp) {
    if (this->ipcameraRtspServerObjAddress == -1) {
        LOGD_T(TAG_H, "cannot the get the rtmp handle address");
        return;
    }
    if (rtspServer == nullptr) {
        LOGD_T(TAG_H, "create the ipcamera rtsp server instance");
        rtspServer = reinterpret_cast<IPCameraRtspServer *>(this->ipcameraRtspServerObjAddress);
    } else {
        LOGD_T(TAG_H, "the ipcamera rtsp server are already created");
    }
    if (this->rtspServer == nullptr) {
        LOGERR(LOG_TAG, "the ipcamera rtsp server are invalid, so just stop encoding data");
        return;
    }
    // int64_t timestamp_t = AdasUtil::currentTimeMillis() / 1000;

    LOGD_T(TAG_H, "current frame flags %d, and encoded raw data size %d", flags, size);
    encoded_frame_queue->push(new RawH264FrameData(data, size));
}

H264DataListenerImpl::~H264DataListenerImpl() {
    LOGD_T(TAG_H, "delete the H264 data listener callback instance ");
    if (this->rtspServer != nullptr) {
        delete rtspServer;
        rtspServer = nullptr;
    }
    ipcameraRtspServerObjAddress = -1;
    if (this->mKyEncoderControllerCallback != nullptr) {
        mKyEncoderControllerCallback = nullptr;
    }
    mKyEncoderControlCallbackAddress = -1;
}

void H264DataListenerImpl::stop() {
    LOGD_T(TAG_H, "stop the h264 data sender, encoding ? %d", encoding);
    AutoMutex lock(mutex);
    if (encoding) {
        LOGD_T(TAG_H, "send an empty frame to encoded_frame_queue to notify stop endoing");
        encoding = false;
        // 这里放置一个空的帧数据，是为了防止当我们停止发送帧时，会导致encoded_frame_queue
        // 在无线等待，而导致程序死循环，引发ANR
        RawH264FrameData *data = new RawH264FrameData(nullptr, 0);
        encoded_frame_queue->push(data);
        pthread_join(send_thread_id, NULL);
    }
    // 通知encoder停止编码
    if (this->mKyEncoderControlCallbackAddress != -1) {
        LOGD_T(TAG_H, "notify the KyEncoder to stop encoding");
        this->mKyEncoderControllerCallback = reinterpret_cast<IKyEncoderControllerCallback *>(mKyEncoderControlCallbackAddress);
        if (this->mKyEncoderControllerCallback != nullptr) {
            this->mKyEncoderControllerCallback->stopEncoding();
        }
    }
    if (encoded_frame_queue != nullptr) {
        LOGD_T(TAG_H, "recycle the encoded_frame_queue");
        delete encoded_frame_queue;
        encoded_frame_queue = nullptr;
    }
    if (this->rtspServer != nullptr) {
        LOGD_T(TAG_H, "stop the rtsp server");
        rtspServer->stopServer();
        delete rtspServer;
        rtspServer = nullptr;
    }
    ipcameraRtspServerObjAddress = -1;
    setupNegotiate = false;
}

void H264DataListenerImpl::storeKyEncoderCallback(IKyEncoderControllerCallback *callback) {
    AutoMutex lock(mutex);
    long callbackObjAddress = reinterpret_cast<long >(callback);
    this->mKyEncoderControlCallbackAddress = callbackObjAddress;
}

void
H264DataListenerImpl::storeIPCameraRtspServerObjAddress(IPCameraRtspServer *ipCameraRtspServer) {
    AutoMutex lock(mutex);
    long objAddress = reinterpret_cast<long> (ipCameraRtspServer);
    LOGD_T(TAG_H, "the address of the IPCameraRtspServer object are %ld", objAddress);
    this->ipcameraRtspServerObjAddress = objAddress;
}
