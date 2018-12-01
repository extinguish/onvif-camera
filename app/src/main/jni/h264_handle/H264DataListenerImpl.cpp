#include <pthread.h>
#include "IH264DataListener.h"
#include "AdasUtil.h"
#include <stdio.h>
#include <fcntl.h>

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

/*
 * 将数据帧发送出去
 */
void *H264DataListenerImpl::send_video_frame() {
    encoding = true;
    LOGD_T(TAG_H, "start send out the video frame %d", encoding);
    while (encoding) {
        std::shared_ptr<H264FrameData *> ptr = encoded_frame_queue.wait_and_pop();
        H264FrameData *encoded_frame = *ptr.get();
        if (encoded_frame != NULL) {
            if (encoded_frame->frame_type == FRAME_TYPE_SPS_N_PPS) {
                LOGD_T(TAG_H, "----> send out the sps and pps frame");
//                int send_sps_n_pps_result = rtmpHandle->sendSpsAndPps(encoded_frame->mSps_data,
//                                                                      encoded_frame->mSps_data_len,
//                                                                      encoded_frame->mPps_data,
//                                                                      encoded_frame->mPps_data_len);
//                if (!send_sps_n_pps_result) {
//                    LOGD_T(TAG_H, "send sps and pps result are %d", send_sps_n_pps_result);
//                    stop();
//                }
            } else if (encoded_frame->frame_type == FRAME_TYPE_VIDEO) {
                LOGD_T(TAG_H, "-----> send out the video frame");
//                int send_video_data_result = rtmpHandle->sendVideoData(
//                        encoded_frame->mVideo_frame_data,
//                        encoded_frame->mVideo_data_len,
//                        encoded_frame->time_stamp);
//                LOGD_T(TAG_H, "send video data result are %d", send_video_data_result);
//                if (!send_video_data_result) {
//                    LOGD_T(TAG_H, "stop send the video data");
//                    stop();
//                }
            } else {
                LOGD_T(TAG_H, "-----> Exit this thread.");
            }
            delete encoded_frame;
        }
    }
    return 0;
}

BYTE *sps = nullptr;
BYTE *pps = nullptr;

const uint8_t pps_len = 4;
uint8_t sps_len = 0;

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
    int64_t timestamp_t = AdasUtil::currentTimeMillis() / 1000;

    LOGD_T(TAG_H, "current frame flags %d, all data size %d", flags, size);

    if (flags == SPS_N_PPS_FRAME_FLAG) {
        LOGD_T(TAG_H, "current frame type are SPS and PPS");
        // sps为[4, size - 8]
        // pps为后四个字节
        if (sps_len == 0) {
            sps_len = static_cast<uint8_t >(size - 12);
        }
        if (sps != nullptr) {
            delete[] sps;
        }
        sps = new BYTE[sps_len];
        for (int32_t index = 0, pps_data_index = 4; pps_data_index < size - 8;) {
            sps[index++] = data[pps_data_index++];
        }
        if (pps != nullptr) {
            delete[] pps;
        }
        pps = new BYTE[pps_len];
        for (int32_t pps_index = 0, data_index = size - 4; data_index < size;) {
            pps[pps_index++] = data[data_index++];
        }
    } else if (flags == KEY_FRAME_FRAME_FLAG) {
        LOGD_T(TAG_H, "current frame type are key frame");
        H264FrameData *sps_n_pps_control_frame = new H264FrameData(FRAME_TYPE_SPS_N_PPS,
                                                                   sps,
                                                                   sps_len,
                                                                   pps,
                                                                   pps_len,
                                                                   NULL,
                                                                   0,
                                                                   timestamp_t);
        encoded_frame_queue.push(sps_n_pps_control_frame);

        H264FrameData *key_frame = new H264FrameData(FRAME_TYPE_VIDEO, NULL, 0, NULL, 0,
                                                     data, size, timestamp_t);
        encoded_frame_queue.push(key_frame);

    } else if (flags == NORMAL_FRAME_FLAG) {
        LOGD_T(TAG_H, "current frame are normal frame ");
        H264FrameData *normal_video_frame = new H264FrameData(FRAME_TYPE_VIDEO,
                                                              NULL, 0, NULL, 0,
                                                              data, size, timestamp_t);
        encoded_frame_queue.push(normal_video_frame);
    }
}


H264DataListenerImpl::~H264DataListenerImpl() {
    LOGD_T(TAG_H, "delete the H264 data listener callback instance ");
    rtmpHandleAddress = -1;
    if (this->mKyEncoderControllerCallback != nullptr) {
        mKyEncoderControllerCallback = nullptr;
    }
    mKyEncoderControlCallbackAddress = -1;
}


void H264DataListenerImpl::stop() {
    LOGD_T(TAG_H, "stop the h264 data sender, encoding ? %d", encoding);
    AutoMutex lock(mutex);
    if (encoding) {
        encoding = false;
        // 这里放置一个空的帧数据，是为了防止当我们停止发送帧时，会导致encoded_frame_queue
        // 在无线等待，而导致程序死循环，引发ANR
        H264FrameData *data = new H264FrameData(0, NULL, 0, NULL, 0, NULL, 0, 0);
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

H264FrameData::H264FrameData(int frame_type,
                             BYTE *sps_data,
                             uint8_t sps_data_len,
                             BYTE *pps_data,
                             uint8_t pps_data_len,
                             BYTE *video_frame_data,
                             int32_t video_data_len,
                             const int64_t time_stamp) :
        frame_type(frame_type),
        mSps_data_len(sps_data_len),
        mPps_data_len(pps_data_len),
        mVideo_data_len(video_data_len),
        time_stamp(time_stamp),
        mSps_data(NULL),
        mPps_data(NULL),
        mVideo_frame_data(NULL) {
    if (sps_data != NULL) {
        mSps_data = static_cast<uint8_t *>(malloc(sps_data_len + 1));
        memcpy(mSps_data, sps_data, sps_data_len);
    }
    if (pps_data != NULL) {
        mPps_data = static_cast<uint8_t *>(malloc(pps_data_len + 1));
        memcpy(mPps_data, pps_data, pps_data_len);
    }
    if (video_frame_data != NULL) {
        mVideo_frame_data = static_cast<uint8_t *>(malloc(video_data_len + 1));
        memcpy(mVideo_frame_data, video_frame_data, static_cast<size_t>(video_data_len));
    }
}

H264FrameData::~H264FrameData() {
    if (mSps_data != NULL) {
        free(mSps_data);
        mSps_data = NULL;
    }
    if (mPps_data != NULL) {
        free(mPps_data);
        mPps_data = NULL;
    }
    if (mVideo_frame_data != NULL) {
        free(mVideo_frame_data);
        mVideo_frame_data = NULL;
    }
};