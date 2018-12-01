//
// Created by fengyin on 18-5-14.
//

#ifndef NETWORKSERVICE_IH264DATALISTENER_H
#define NETWORKSERVICE_IH264DATALISTENER_H

#include <stdio.h>
#include "../simple_utils.h"
#include "Mutex.h"
#include "ThreadQueue.hpp"
#include "IKyEncoderControllerCallback.h"

#define BYTE uint8_t

#define SPS_N_PPS_FRAME_FLAG 2
#define KEY_FRAME_FRAME_FLAG 9
#define NORMAL_FRAME_FLAG 8

const static int FRAME_TYPE_SPS_N_PPS = 10;
const static int FRAME_TYPE_VIDEO = 20;

class H264FrameData {
public:
    // 用于判断编码之后的帧的类型
    // 帧的类型分为两种,sps_n_pps控制帧以及普通的视频帧
    int frame_type;

    // sps和pps
    BYTE *mSps_data;
    uint8_t mSps_data_len;
    BYTE *mPps_data;
    uint8_t mPps_data_len;

    // 正常的视频帧
    BYTE *mVideo_frame_data;
    int32_t mVideo_data_len;

    // 帧的时间戳
    int64_t time_stamp;
public:

    H264FrameData(int frame_type, BYTE *sps_data, uint8_t sps_data_len,
                  BYTE *pps_data, uint8_t pps_data_len, BYTE *video_frame_data,
                  int32_t video_data_len, const int64_t time_stamp);

    ~H264FrameData();
};

class IH264DataListener {
public:
    /**
     * H264 data callback interface.
     * @param data h264 data
     * @param flags 2:encode flag, 9:keyframe, 8:normal frame
     * @param offset
     * @param size
     */
    virtual void onVideoFrame(uint8_t *data, uint32_t flags, int32_t offset, int32_t size,
                              int64_t time_stamp) = 0;
};

class H264DataListenerImpl : public IH264DataListener {

public:
    H264DataListenerImpl();

    virtual void start();

    virtual void stop();

    virtual void
    onVideoFrame(uint8_t *data, uint32_t flags, int32_t offset, int32_t size, int64_t time_stamp);

    virtual void storeKyEncoderCallback(IKyEncoderControllerCallback *callback);

    ~H264DataListenerImpl();

private:
    long rtmpHandleAddress;

    long mKyEncoderControlCallbackAddress;
    IKyEncoderControllerCallback *mKyEncoderControllerCallback;

    ThreadQueue<H264FrameData *> encoded_frame_queue;

    pthread_t send_thread_id;

    void *send_video_frame();

    static void *send(void *data_callback);

    bool encoding = false;
    Mutex mutex;
};

#endif //NETWORKSERVICE_IH264DATALISTENER_H
