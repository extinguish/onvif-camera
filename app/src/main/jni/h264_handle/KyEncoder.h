//
// Created by fengyin on 17-11-10.
//

#ifndef MODULE_KYENCODER_H
#define MODULE_KYENCODER_H

#include <jni.h>

#include <stdint.h>
#include <pthread.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaError.h>
#include <media/NdkMediaFormat.h>
#include <media/NdkMediaMuxer.h>
#include "CodecParams.h"
#include "log.h"
#include "ThreadQueue.hpp"
#include "Mutex.h"
#include "IH264DataListener.h"

typedef void (*H264Callback)(uint8_t *data, int32_t offset, int32_t size);

const static int kVideoWidth = 320;
const static int kVideoHeight = 240;

class KyEncoder : public IKyEncoderControllerCallback {
public:
    KyEncoder(IH264DataListener *listener);

    ~KyEncoder();

    int16_t prepare(const CodecParams *params, const CodecOutputFile *file = NULL);

    int16_t start();

    int16_t stop();

    int16_t release();

    bool isRecording() const { return mRecording; };

    bool isStopEncoding() { return mStopEncoding; }

    void feedData(void *data, int width, int height);

    void setH264Callback(const H264Callback callback) { mH264Callback = callback; }

    virtual void stopEncoding();

private:
    void encode();

private:
    static void *run(void *addr);

private:
    int64_t mStartTime;
    Mutex mMutex;
    pthread_t mPid;
    bool mRecording;
    // 用于标记当前是否已经停止编码了
    // mStopEncoding是在encoding失败之后，或者发送编码数据数据失败之后，会被设置为true
    // 而mRecoding本身是KyEncoder内部用于标示当前的编码状态的，即mRecoding是供内部使用
    // 而mStopEncoding是供外部使用，即供外部来判断KyEncoder的状态的
    bool mStopEncoding;

    AMediaCodecBufferInfo *mBufferInfo;
    AMediaCodec *mCodec;
    H264Callback mH264Callback;

    int mVideoTrack;
    ThreadQueue<void *> frame_queue;

    CodecParams mCodecParams;
    unsigned char *mDataBuffer;

    IH264DataListener *mListener;
};

#endif //MODULE_KYENCODER_H
