// 用于KyEncoder中间层
// 我们直接将之前Java层通过调用MediaCodec接口进行编码的实现
// 移动到通过KyEncoder来进行编码操作
#include <jni.h>
#include <string.h>
#include <stdlib.h>

#include "simple_utils.h"
#include "h264_handle/KyEncoder.h"
#include "h264_handle/IH264DataListener.h"
#include "SimpleController.hpp"

#define TAG_J "rtsp_service"

#define JAVA_CLASS_NAME "com/adasplus/ipcamera/encoder/KyEncoderWrapper"

#define ENCODE_WIDTH 960
#define ENCODE_HEIGHT 480
#define RTMP_CONNECT_TIMEOUT 5000

#ifdef __cplusplus
extern "C" {
#endif

static jlong native_create(JNIEnv *env, jobject thiz) {
    LOGD_T(TAG_J, "create the encode ");
    SimpleController *controller = new SimpleController();
    controller->init(ENCODE_WIDTH, ENCODE_HEIGHT,
                     RTMP_CONNECT_TIMEOUT);
    jlong controller_address = reinterpret_cast<jlong>(controller);

    return controller_address;
}

static jboolean native_prepare(JNIEnv *env, jobject thiz, jlong objAddress, jobject encodeParams) {
    LOGI_T(TAG_J, "prepare the encoder instance of address %lld", objAddress);
    CodecParams codecParams;
    memset(&codecParams, 0, sizeof(CodecParams));
    jclass encodeParamJavaCls = env->FindClass(
            "com/adasplus/rtmp/encoder/KyEncoderWrapper$EncodeParam");
    jfieldID widthFieldId = env->GetFieldID(encodeParamJavaCls, "width", "I");
    codecParams.width = (uint16_t) env->GetIntField(encodeParams, widthFieldId);
    jfieldID heightFieldId = env->GetFieldID(encodeParamJavaCls, "height", "I");
    codecParams.height = (uint16_t) env->GetIntField(encodeParams, heightFieldId);
    jfieldID frameRateFieldId = env->GetFieldID(encodeParamJavaCls, "frameRate", "I");
    codecParams.frameRate = (uint16_t) env->GetIntField(encodeParams, frameRateFieldId);
    jfieldID colorFormatFieldId = env->GetFieldID(encodeParamJavaCls, "colorFormat", "I");
    codecParams.colorFormat = (uint32_t) env->GetIntField(encodeParams, colorFormatFieldId);
    jfieldID bitRateFieldId = env->GetFieldID(encodeParamJavaCls, "bitRate", "J");
    codecParams.bitRate = (uint32_t) env->GetLongField(encodeParams, bitRateFieldId);

    SimpleController *controller = reinterpret_cast<SimpleController *>(objAddress);
    KyEncoder *kyEncoder = controller->getKyEncoder();

    if (kyEncoder == NULL) {
        LOGERR(TAG_J, "fail to get the KyEncoder instance");
        return JNI_FALSE;
    }
    if (kyEncoder->prepare(&codecParams) == -1) {
        LOGERR(TAG_J, "fail to prepare the KyEncoder");
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

/**
 * 我们需要使两个encoder同时运行
 * 前路摄像头工作时，后路摄像头也可以同时工作。两个之间不会相互影响
 */
static jboolean native_start(JNIEnv *env, jobject thiz, jlong objAddress) {
    LOGD_T(TAG_J, "start the encoder of address in %lld", objAddress);
    SimpleController *controller = reinterpret_cast<SimpleController *>(objAddress);
    KyEncoder *kyEncoder = controller->getKyEncoder();
    if (kyEncoder == NULL) {
        LOGERR(TAG_J, "fail to get the KyEncoder instance ");
        return JNI_FALSE;
    }
    if (!kyEncoder->start()) {
        LOGERR(TAG_J, "fail to start the KyEncoder");
        return JNI_FALSE;
    }
    H264DataListenerImpl *h264DataListener = controller->getH264DataListenerImpl();

    h264DataListener->start();
    return JNI_TRUE;
}

static jboolean
native_encode_frame(JNIEnv *env, jobject thiz, jlong objAddress, jbyteArray frame, jint frame_width,
                    jint frame_height) {
    LOGD_T(TAG_J, "encode frame with encoder instance of address %lld", objAddress);
    jbyte *buffer = env->GetByteArrayElements(frame, 0);
    SimpleController *controller = reinterpret_cast<SimpleController *>(objAddress);
    KyEncoder *encoder = controller->getKyEncoder();
    if (encoder == NULL) {
        LOGERR(TAG_J, "fail to get the KyEncoder instance ");
        return JNI_FALSE;
    }
    if (encoder->isRecording()) {
        encoder->feedData((void *) buffer, frame_width, frame_height);
    }

    env->ReleaseByteArrayElements(frame, buffer, 0);

    if (encoder->isStopEncoding()) {
        LOGERR(TAG_J, "send data failed, and stop encoding data");
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

static jboolean native_stop(JNIEnv *env, jobject thiz, jlong objAddress) {
    LOGI_T(TAG_J, "stop the encoder of address %lld", objAddress);
    SimpleController *controller = reinterpret_cast<SimpleController *>(objAddress);
    KyEncoder *encoder = controller->getKyEncoder();
    if (encoder == NULL) {
        LOGERR(TAG_J, "fail to get the KyEncoder instance");
        return JNI_FALSE;
    }
    encoder->stop();

    controller->getH264DataListenerImpl()->stop();
    return JNI_TRUE;
}

static jboolean native_destroy(JNIEnv *env, jobject thiz, jlong objAddress) {
    LOGI_T(TAG_J, "destroy the encoder of address %lld", objAddress);
    SimpleController *controller = reinterpret_cast<SimpleController *>(objAddress);

    if (controller == NULL) {
        LOGERR(TAG_J, "fail to get the SimpleController instance ");
        return JNI_FALSE;
    }

    delete controller;
    return JNI_TRUE;
}

static jboolean native_release(JNIEnv *env, jobject thiz, jlong objAddress) {
    LOGI_T(TAG_J, "release the encoder of address %lld", objAddress);
    SimpleController *controller = reinterpret_cast<SimpleController *>(objAddress);
    KyEncoder *kyEncoder = controller->getKyEncoder();
    if (kyEncoder == NULL) {
        LOGERR(TAG_J, "fail to get the KyEncoder instance ");
        return JNI_FALSE;
    }
    kyEncoder->release();
    return JNI_TRUE;
}

static JNINativeMethod gMethods[] = {
        // long create()
        {"create",      "()J",                                                              (void *) native_create},
        // boolean prepare(long, com.adasplus.ipcamera.encoder.KyEncoderWrapper$EncodeParam)
        {"prepare",     "(JLcom/adasplus/ipcamera/encoder/KyEncoderWrapper$EncodeParam;)Z", (void *) native_prepare},
        // boolean start(long)
        {"start",       "(J)Z",                                                             (void *) native_start},
        // boolean stop(long)
        {"stop",        "(J)Z",                                                             (void *) native_stop},
        // boolean destroy(long)
        {"destroy",     "(J)Z",                                                             (void *) native_destroy},
        // boolean encodeFrame(long, byte[], int, int)
        {"encodeFrame", "(J[BII)Z",                                                         (void *) native_encode_frame},
        // boolean release(long)
        {"release",     "(J)Z",                                                             (void *) native_release}
};

static int registerNativeMethods(JNIEnv *env, const char *className, JNINativeMethod *gMethods,
                                 int methodNums) {
    jclass jClazz;
    jClazz = env->FindClass(className);
    if (jClazz == NULL) {
        LOGD_T(TAG_J, "fail to find the class %s", className);
        return JNI_FALSE;
    }
    if ((env->RegisterNatives(jClazz, gMethods, methodNums)) < 0) {
        LOGD_T(TAG_J, "fail to register native method ");
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

static int registerAllNativeMethods(JNIEnv *env) {
    LOGD_T(TAG_J, "register all native methods");
    if (!registerNativeMethods(env, JAVA_CLASS_NAME,
                               gMethods, sizeof(gMethods) / sizeof(gMethods[0]))) {
        LOGD_T(TAG_J, "fail to register all native methods ");
        return FALSE;
    }

    return TRUE;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    LOGD_T(TAG_J, "start jni load environment");
    JNIEnv *env = NULL;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_4) != JNI_OK) {
        LOGD_T(TAG_J, "fail to get the Java vm env");
        return -1;
    }

    if (!registerAllNativeMethods(env)) {
        return -1;
    }

    jint result = JNI_VERSION_1_4;

    return result;
}

#ifdef __cplusplus
}
#endif
