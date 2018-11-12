#include <jni.h>
#include <string.h>
#include <stdlib.h>

#include <libyuv.h>

#include "log.h"
#include "color_converter.h"

#define JAVA_CLASS_NAME "net/majorkernelpanic/streaming/hw/NativeYUVConverter"


extern "C" {
static void
native_nv21_to_yuv420sp(JNIEnv *env, jobject thiz, jbyteArray srcarray, jbyteArray dstarray,
                        jint ySize) {
    jbyte *src = env->GetByteArrayElements(srcarray, 0);
    jbyte *dst = env->GetByteArrayElements(dstarray, 0);
    NV21TOYUV420SP(reinterpret_cast<const unsigned char *>(src),
                   reinterpret_cast<const unsigned char *>(dst),
                   ySize);
    env->ReleaseByteArrayElements(srcarray, src, JNI_ABORT);
    env->ReleaseByteArrayElements(dstarray, dst, JNI_ABORT);
}
}

extern "C" {
static void
native_nv21_to_yuv420p(JNIEnv *env, jobject thiz, jbyteArray srcarray, jbyteArray dstarray,
                       jint ySize) {
    jbyte *src = env->GetByteArrayElements(srcarray, 0);
    jbyte *dst = env->GetByteArrayElements(dstarray, 0);
    YUV420SPTOYUV420P(reinterpret_cast<const unsigned char *>(src),
                      reinterpret_cast<const unsigned char *>(dst),
                      ySize);
    env->ReleaseByteArrayElements(srcarray, src, JNI_ABORT);
    env->ReleaseByteArrayElements(dstarray, dst, JNI_ABORT);
}
}

extern "C" {
static void
native_yuv420sp_to_yuv420p(JNIEnv *env, jobject thiz, jbyteArray srcarray, jbyteArray dstarray,
                           jint ySize) {
    jbyte *src = env->GetByteArrayElements(srcarray, 0);
    jbyte *dst = env->GetByteArrayElements(dstarray, 0);
    NV21TOYUV420P(reinterpret_cast<const unsigned char *>(src),
                  reinterpret_cast<const unsigned char *>(dst),
                  ySize);
    env->ReleaseByteArrayElements(srcarray, src, JNI_ABORT);
    env->ReleaseByteArrayElements(dstarray, dst, JNI_ABORT);
}
}

extern "C" {
static void
native_nv21_to_argb(JNIEnv *env, jobject thiz, jbyteArray srcarray, jintArray dstarray, jint width,
                    jint height) {
    jbyte *src = env->GetByteArrayElements(srcarray, 0);
    unsigned int *dst = (unsigned int *) env->GetIntArrayElements(dstarray, 0);
    NV21TOARGB(reinterpret_cast<const unsigned char *>(src), dst, width, height);
    env->ReleaseByteArrayElements(srcarray, src, JNI_ABORT);
    env->ReleaseIntArrayElements(dstarray, reinterpret_cast<jint *>(dst), JNI_ABORT);
}
}

extern "C" {
static void
native_fix_gl_pixel(JNIEnv *env, jobject thiz, jintArray srcarray, jintArray dstarray, jint w,
                    jint h) {
    unsigned int *src = (unsigned int *) env->GetIntArrayElements(srcarray, 0);
    unsigned int *dst = (unsigned int *) env->GetIntArrayElements(dstarray, 0);
    FIXGLPIXEL(src, dst, w, h);
    env->ReleaseIntArrayElements(srcarray, reinterpret_cast<jint *>(src), JNI_ABORT);
    env->ReleaseIntArrayElements(dstarray, reinterpret_cast<jint *>(dst), JNI_ABORT);
}
}

extern "C" {
static void
native_nv21_transform(JNIEnv *env, jobject thiz, jbyteArray srcarray, jbyteArray dstarray,
                      jint srcwidth, jint srcheight, jint directionflag) {
    jbyte *src = env->GetByteArrayElements(srcarray, 0);
    jbyte *dst = env->GetByteArrayElements(dstarray, 0);
    NV21Transform(reinterpret_cast<const unsigned char *>(src),
                  reinterpret_cast<const unsigned char *>(dst),
                  srcwidth, srcheight,
                  directionflag);
    env->ReleaseByteArrayElements(srcarray, src, JNI_ABORT);
    env->ReleaseByteArrayElements(dstarray, dst, JNI_ABORT);
}
}

/**
 * 采用libyuv进行nv通道转换
 */
static jbyteArray
native_yu12_to_i420(JNIEnv *env, jobject thiz, jbyteArray src_arr, jint src_width,
                    jint src_height) {
    jbyte *src_frame = env->GetByteArrayElements(src_arr, NULL);
    const int dst_width = src_width / 2;
    const int dst_height = src_height / 2;

    jbyteArray dst_array = env->NewByteArray(dst_width * dst_height * 3 / 2);
    jbyte *dst_frame = env->GetByteArrayElements(dst_array, NULL);

    libyuv::I420Scale(reinterpret_cast<const uint8 *>(src_frame),
                      src_width,
                      reinterpret_cast<const uint8 *>(src_frame) + src_width * src_height,
                      src_width / 2,
                      reinterpret_cast<const uint8 *>(src_frame) + src_width * src_height * 5 / 4,
                      src_width / 2,
                      src_width,
                      src_height,
                      reinterpret_cast<uint8 *>(dst_frame),
                      dst_width,
                      reinterpret_cast<uint8 *>(dst_frame) + dst_width * dst_height * 5 / 4,
                      dst_width / 2,
                      reinterpret_cast<uint8 *>(dst_frame) + dst_width * dst_height,
                      dst_width / 2,
                      dst_width,
                      dst_height,
                      libyuv::kFilterNone);


    env->ReleaseByteArrayElements(src_arr, src_frame, JNI_ABORT);
    // env->ReleaseByteArrayElements(dst_array, dst_frame, JNI_ABORT);
    return dst_array;
}

static JNINativeMethod gMethods[] = {
        // void nv21ToYUV420SP(byte[], byte[], int);
        {"nv21ToYUV420SP",    "([B[BI)V",   (void *) native_nv21_to_yuv420sp},
        // void nv21ToYUV420P(byte[], byte[], int);
        {"nv21ToYUV420P",     "([B[BI)V",   (void *) native_nv21_to_yuv420p},
        // void yuv420SPToYUV420P(byte[], byte[], int);
        {"yuv420SPToYUV420P", "([B[BI)V",   (void *) native_yuv420sp_to_yuv420p},
        // void nv21TOARGB(byte[], int[], int, int);
        {"nv21TOARGB",        "([B[III)V",  (void *) native_nv21_to_argb},
        // void fixGLPixel(int[], int[], int, int);
        {"fixGLPixel",        "([I[III)V",  (void *) native_fix_gl_pixel},
        // void nv21Transform(byte[], byte[], int, int, int);
        {"nv21Transform",     "([B[BIII)V", (void *) native_nv21_transform},
        // byte[] yv12ToI420(byte[], int, int);
        {"yv12ToI420",        "([BII)[B",   (void *) native_yu12_to_i420}
};

static int registerNativeMethods(JNIEnv *env, const char *className, JNINativeMethod *gMethods,
                                 int methodNums) {
    jclass jClazz;
    jClazz = env->FindClass(className);
    if (jClazz == NULL) {
        return JNI_FALSE;
    }
    if ((env->RegisterNatives(jClazz, gMethods, methodNums)) < 0) {
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

static int registerAllNativeMethods(JNIEnv *env) {
    if (!registerNativeMethods(env, JAVA_CLASS_NAME,
                               gMethods, sizeof(gMethods) / sizeof(gMethods[0]))) {
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = NULL;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_4) != JNI_OK) {
        return -1;
    }

    if (!registerAllNativeMethods(env)) {
        return -1;
    }

    jint result = JNI_VERSION_1_4;

    return result;
}
