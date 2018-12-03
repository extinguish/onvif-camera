#include <pthread.h>
#include "IH264DataListener.h"
#include "AdasUtil.h"
#include <stdio.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/uio.h>

#define TAG_H "rtmp_service"

H264DataListenerImpl::H264DataListenerImpl() {
}

void H264DataListenerImpl::start() {
    pthread_create(&send_thread_id, NULL, H264DataListenerImpl::send, this);
}

void *H264DataListenerImpl::send(void *data_callback) {
    H264DataListenerImpl *data_listener = reinterpret_cast<H264DataListenerImpl *>(data_callback);
    data_listener->send_video_frame();
    pthread_exit(NULL);
}

// TODO: 但是这样有一个问题，就是我们的这个文件会越来越大，到时候会引出另外一个问题
static const char* file_name = "/sdcard/cache_video.file";

/*
 * 将数据帧发送出去
 */
void *H264DataListenerImpl::send_video_frame() {
    encoding = true;
    LOGD_T(TAG_H, "start writing data to local file %d", encoding);
    while (encoding) {
        std::shared_ptr<RawH264FrameData *> ptr = encoded_frame_queue.wait_and_pop();
        RawH264FrameData *encoded_frame = *ptr.get();
        if (encoded_frame != NULL) {
            // TODO: 将数据写入到本地当中
            if (encodedDataFd == -1) {
                // 创建文件
                encodedDataFd = open(file_name, O_CREAT | O_APPEND | O_WRONLY, 0664);
            }
            struct iovec vec[1];
            vec[0].iov_base = (void *) encoded_frame->raw_frame;
            vec[0].iov_len = strlen(reinterpret_cast<const char *>(encoded_frame->raw_frame)) + 1;
            int write_size = writev(encodedDataFd, vec, 1);
            LOGD_T(TAG_H, "write encoded frame data to local with size of %d", write_size);
            delete encoded_frame;
        }
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
    if (this->rtmpHandleAddress == -1) {
        LOGD_T(TAG_H, "cannot the get the rtmp handle address");
        return;
    }
    // int64_t timestamp_t = AdasUtil::currentTimeMillis() / 1000;

    LOGD_T(TAG_H, "current frame flags %d, all data size %d", flags, size);
    encoded_frame_queue.push(new RawH264FrameData(data));
}

H264DataListenerImpl::~H264DataListenerImpl() {
    LOGD_T(TAG_H, "delete the H264 data listener callback instance ");
    rtmpHandleAddress = -1;
    if (this->mKyEncoderControllerCallback != nullptr) {
        mKyEncoderControllerCallback = nullptr;
    }
    mKyEncoderControlCallbackAddress = -1;
    if (this->encodedDataFd != -1) {
        // 关闭encodedDataFd
        close(encodedDataFd);
        this->encodedDataFd = -1;
    }
}

void H264DataListenerImpl::stop() {
    LOGD_T(TAG_H, "stop the h264 data sender, encoding ? %d", encoding);
    AutoMutex lock(mutex);
    if (encoding) {
        encoding = false;
        // 这里放置一个空的帧数据，是为了防止当我们停止发送帧时，会导致encoded_frame_queue
        // 在无线等待，而导致程序死循环，引发ANR
        RawH264FrameData *data = new RawH264FrameData(nullptr);
        encoded_frame_queue.push(data);
        pthread_join(send_thread_id, NULL);
    }
    // 通知encoder停止编码
    if (this->mKyEncoderControlCallbackAddress != -1) {
        this->mKyEncoderControllerCallback = reinterpret_cast<IKyEncoderControllerCallback *>(mKyEncoderControlCallbackAddress);
        if (this->mKyEncoderControllerCallback != nullptr) {
            this->mKyEncoderControllerCallback->stopEncoding();
        }
    }
    rtmpHandleAddress = -1;
}

void H264DataListenerImpl::storeKyEncoderCallback(IKyEncoderControllerCallback *callback) {
    AutoMutex lock(mutex);
    long callbackObjAddress = reinterpret_cast<long >(callback);
    this->mKyEncoderControlCallbackAddress = callbackObjAddress;
}

int H264DataListenerImpl::getEncodedDataFd() {
    return this->encodedDataFd;
}

RawH264FrameData::RawH264FrameData(BYTE *raw_frame) : raw_frame(raw_frame) {
}

RawH264FrameData::~RawH264FrameData() {
    if (raw_frame != nullptr) {
        delete raw_frame;
    }
}
