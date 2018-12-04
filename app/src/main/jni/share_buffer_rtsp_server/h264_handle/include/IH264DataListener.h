//
// Created by fengyin on 18-5-14.
//

#ifndef NETWORKSERVICE_IH264DATALISTENER_H
#define NETWORKSERVICE_IH264DATALISTENER_H

#include <stdio.h>
#include "../../simple_utils.h"
#include "Mutex.h"
#include "../../ThreadQueue.hpp"
#include "IKyEncoderControllerCallback.h"
#include "../../share_buffer_rtsp_server/include/IPCameraRtspServer.h"
#include "../../RawFrame.hpp"

#define BYTE uint8_t

//#define SPS_N_PPS_FRAME_FLAG 2
//#define KEY_FRAME_FRAME_FLAG 9
//#define NORMAL_FRAME_FLAG 8

//const static int FRAME_TYPE_SPS_N_PPS = 10;
//const static int FRAME_TYPE_VIDEO = 20;

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

    void storeIPCameraRtspServerObjAddress(IPCameraRtspServer *ipcameraRtspServerObj);

    ~H264DataListenerImpl();

private:
    IPCameraRtspServer *rtspServer;
    long ipcameraRtspServerObjAddress;

    long mKyEncoderControlCallbackAddress;
    IKyEncoderControllerCallback *mKyEncoderControllerCallback;

    // encoded_frame_queue在H264DataListenerImpl当中只是负责数据的添加
    // 数据的获取是在V4L2DeviceSource当中
    ThreadQueue<RawH264FrameData *> *encoded_frame_queue = new ThreadQueue<RawH264FrameData *>();

    pthread_t send_thread_id;

    void *send_video_frame();

    static void *send(void *data_callback);

    bool encoding = false;
    // 是否将编码好的h264数据队列传递给DeviceSource
    bool setupNegotiate = false;
    Mutex mutex;

};

#endif //NETWORKSERVICE_IH264DATALISTENER_H
