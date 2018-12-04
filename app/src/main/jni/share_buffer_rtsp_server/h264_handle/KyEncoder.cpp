//
// Created by fengyin on 17-11-10.
//

#include "include/KyEncoder.h"
#include "include/AdasUtil.h"
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/types.h>

int64_t systemnanotime() {
    timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    return now.tv_sec * 1000000000LL + now.tv_nsec;
}

KyEncoder::KyEncoder(IH264DataListener *listener) : mRecording(false),
                                                    mCodec(NULL),
                                                    mVideoTrack(-1),
                                                    mBufferInfo(NULL),
                                                    mH264Callback(NULL),
                                                    mDataBuffer(NULL),
                                                    mListener(listener) {

}

KyEncoder::~KyEncoder() {
    if (mListener != NULL) {
        mListener = NULL;
    }
}

int16_t KyEncoder::prepare(const CodecParams *params, const CodecOutputFile *file) {
    if (params != NULL && mCodec == NULL) {
        AMediaFormat *format = AMediaFormat_new();
        if (format == NULL) {
            LOGE("Cannot create format!");
            return -1;
        }
        memcpy(&mCodecParams, params, sizeof(CodecParams));
        LOGE("width %d height %d bitrate %d frameRate %d colorformat %02x)", kVideoWidth,
             kVideoHeight,
             params->bitRate, params->frameRate, params->colorFormat);
        AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, VIDEO_MIME);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_WIDTH, kVideoWidth);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_HEIGHT, kVideoHeight);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_BIT_RATE, params->bitRate);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_FRAME_RATE, params->frameRate);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_I_FRAME_INTERVAL, 1);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_COLOR_FORMAT, params->colorFormat);
        uint8_t sps[2] = {0x12, 0x12};
        uint8_t pps[2] = {0x12, 0x12};
        AMediaFormat_setBuffer(format, "csd-0", sps, 2); // sps
        AMediaFormat_setBuffer(format, "csd-1", pps, 2); // pps
        mCodec = AMediaCodec_createEncoderByType(VIDEO_MIME);
        if (mCodec == NULL) {
            LOGE("Create MediaCodec fail!");
            return -1;
        }
        media_status_t status = AMediaCodec_configure(mCodec, format, NULL, NULL,
                                                      AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
        if (status != AMEDIA_OK) {
            LOGE("Set video fail%d", status);
            return -1;
        }
        AMediaFormat_delete(format);
        if (mDataBuffer != NULL) {
            free(mDataBuffer);
            mDataBuffer = NULL;
        }
        mDataBuffer = (unsigned char *) malloc(kVideoWidth * kVideoHeight * 3 / 2);
        if (mDataBuffer == NULL) {
            LOGE("Malloc mDataBuffer fail!");
        }
        if (mBufferInfo != NULL) {
            free(mBufferInfo);
            mBufferInfo = NULL;
        }
        mBufferInfo = (AMediaCodecBufferInfo *) malloc(sizeof(AMediaCodecBufferInfo));
        if (mBufferInfo == NULL) {
            LOGE("Malloc mBufferInfo fail!");
        }
        LOGE("Init media codec success!");
    } else {
        LOGE("CodecParams should be not null!");
    }
    return 1;
}

int16_t KyEncoder::start() {
    LOGE("KyEncoder start with recording ? %d", mRecording);
    if (!mRecording) {
        mStartTime = systemnanotime();
        mVideoTrack = -1;
        AMediaCodec_start(mCodec);
        pthread_create(&mPid, NULL, KyEncoder::run, this);
        return 1;
    }
    return 0;
}

int16_t KyEncoder::stop() {
    AutoMutex l(mMutex);
    if (mRecording) {
        mRecording = false;

        pthread_join(mPid, NULL);
        mVideoTrack = -1;
        if (mCodec != NULL) {
            AMediaCodec_flush(mCodec);
            AMediaCodec_stop(mCodec);
        }
    }
    return 1;
}

int16_t KyEncoder::release() {
    LOGE("KyEncoder release");
    AutoMutex l(mMutex);
    if (mCodec != NULL) {
        AMediaCodec_delete(mCodec);
        mCodec = NULL;
    }

    if (mDataBuffer != NULL) {
        free(mDataBuffer);
        mDataBuffer = NULL;
    }

    if (mBufferInfo != NULL) {
        free(mBufferInfo);
        mBufferInfo = NULL;
    }

    return 1;
}

void KyEncoder::feedData(void *data, int width, int height) {
    if (!mRecording) {
        return;
    }
    AdasUtil::yv12ToI420((unsigned char *) data, width, height,
                         mDataBuffer, kVideoWidth, kVideoHeight);
    LOGD("push data into frame queue");
    frame_queue.push(mDataBuffer);
}

void KyEncoder::encode() {
    pthread_setname_np(mPid, "EncoderThread");
    mRecording = true;
    mStopEncoding = false;
    while (mRecording) {
        if (!frame_queue.empty()) {
            ssize_t index = AMediaCodec_dequeueInputBuffer(mCodec, -1);
            if (index >= 0) {
                size_t out_size;
                uint8_t *buffer = AMediaCodec_getInputBuffer(mCodec, index, &out_size);
                void *data = *frame_queue.wait_and_pop().get();
                if (data != NULL && out_size > 0) {
                    memcpy(buffer, data, out_size);
                    AMediaCodec_queueInputBuffer(mCodec,
                                                 index,
                                                 0, out_size,
                                                 (systemnanotime() - mStartTime) / 1000,
                                                 mRecording ? 0
                                                            : AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
                    LOGD("encoding with out_size %d", out_size);
                }
            }
            ssize_t outIndex;
            do {
                size_t out_size;
                outIndex = AMediaCodec_dequeueOutputBuffer(mCodec, mBufferInfo, 0);
                LOGD("encoding with out index of %d", outIndex);
                if (outIndex >= 0) {
                    uint8_t *outBuffer = AMediaCodec_getOutputBuffer(mCodec, outIndex, &out_size);
//                    mListener->onVideoFrame(outBuffer, mBufferInfo->offset, mBufferInfo->size);
                    mListener->onVideoFrame(outBuffer, mBufferInfo->flags, mBufferInfo->offset,
                                            mBufferInfo->size,
                                            mBufferInfo->presentationTimeUs / 1000);
                    AMediaCodec_releaseOutputBuffer(mCodec, outIndex, false);
                } else if (outIndex == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
                    AMediaFormat *outFormat = AMediaCodec_getOutputFormat(mCodec);
                    const char *s = AMediaFormat_toString(outFormat);
                    LOGE("video out format %s", s);
                    LOGE("add video track status-->%d", mVideoTrack);
                }
            } while (outIndex >= 0);
        } else {
            usleep(3000);
        }
    }
    LOGE("End of encode!");
}

void *KyEncoder::run(void *addr) {
    KyEncoder *encoder = reinterpret_cast<KyEncoder *>(addr);
    encoder->encode();
    LOGE("ky_encoder thread exit.");
    pthread_exit(NULL);
    return NULL;
}

void KyEncoder::stopEncoding() {
    this->stop();
    this->release();
    mStopEncoding = true;
}
