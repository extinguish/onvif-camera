#include <jni.h>
#include <string.h>
#include <stdlib.h>

#include "log.h"
#include "color_converter.h"

#define JAVA_CLASS_NAME "net/majorkernelpanic/streaming/hw/NativeYUVConverter"

static void
native_nv21_to_yuv420sp(JNIEnv *env, jobject thiz, jbyteArray srcarray, jbyteArray dstarray,
                        jint ySize) {
    unsigned char *src = (unsigned char *) (*env)->GetByteArrayElements(env, srcarray, 0);
    unsigned char *dst = (unsigned char *) (*env)->GetByteArrayElements(env, dstarray, 0);
    NV21TOYUV420SP(src, dst, ySize);
    (*env)->ReleaseByteArrayElements(env, srcarray, src, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, dstarray, dst, JNI_ABORT);
}

static void
native_nv21_to_yuv420p(JNIEnv *env, jobject thiz, jbyteArray srcarray, jbyteArray dstarray,
                       jint ySize) {
    unsigned char *src = (unsigned char *) (*env)->GetByteArrayElements(env, srcarray, 0);
    unsigned char *dst = (unsigned char *) (*env)->GetByteArrayElements(env, dstarray, 0);
    YUV420SPTOYUV420P(src, dst, ySize);
    (*env)->ReleaseByteArrayElements(env, srcarray, src, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, dstarray, dst, JNI_ABORT);
}

static void
native_yuv420sp_to_yuv420p(JNIEnv *env, jobject thiz, jbyteArray srcarray, jbyteArray dstarray,
                           jint ySize) {
    unsigned char *src = (unsigned char *) (*env)->GetByteArrayElements(env, srcarray, 0);
    unsigned char *dst = (unsigned char *) (*env)->GetByteArrayElements(env, dstarray, 0);
    NV21TOYUV420P(src, dst, ySize);
    (*env)->ReleaseByteArrayElements(env, srcarray, src, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, dstarray, dst, JNI_ABORT);
}

static void
native_nv21_to_argb(JNIEnv *env, jobject thiz, jbyteArray srcarray, jintArray dstarray, jint width,
                    jint height) {
    unsigned char *src = (unsigned char *) (*env)->GetByteArrayElements(env, srcarray, 0);
    unsigned int *dst = (unsigned int *) (*env)->GetIntArrayElements(env, dstarray, 0);
    NV21TOARGB(src, dst, width, height);
    (*env)->ReleaseByteArrayElements(env, srcarray, src, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, dstarray, dst, JNI_ABORT);
}

static void
native_fix_gl_pixel(JNIEnv *env, jobject thiz, jintArray srcarray, jintArray dstarray, jint w,
                    jint h) {
    unsigned int *src = (unsigned int *) (*env)->GetIntArrayElements(env, srcarray, 0);
    unsigned int *dst = (unsigned int *) (*env)->GetIntArrayElements(env, dstarray, 0);
    FIXGLPIXEL(src, dst, w, h);
    (*env)->ReleaseIntArrayElements(env, srcarray, src, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, dstarray, dst, JNI_ABORT);
}

static void
native_nv21_transform(JNIEnv *env, jobject thiz, jbyteArray srcarray, jbyteArray dstarray,
                      jint srcwidth, jint srcheight, jint directionflag) {
    unsigned char *src = (unsigned char *) (*env)->GetByteArrayElements(env, srcarray, 0);
    unsigned char *dst = (unsigned char *) (*env)->GetByteArrayElements(env, dstarray, 0);
    NV21Transform(src, dst, srcwidth, srcheight, directionflag);
    (*env)->ReleaseByteArrayElements(env, srcarray, src, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, dstarray, dst, JNI_ABORT);
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
        {"nv21Transform",     "([B[BIII)V", (void *) native_nv21_transform}
};

static int registerNativeMethods(JNIEnv *env, const char *className, JNINativeMethod *gMethods,
                                 int methodNums) {
    jclass jClazz;
    jClazz = (*env)->FindClass(env, className);
    if (jClazz == NULL) {
        return JNI_FALSE;
    }
    if (((*env)->RegisterNatives(env, jClazz, gMethods, methodNums)) < 0) {
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
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_4) != JNI_OK) {
        return -1;
    }

    if (!registerAllNativeMethods(env)) {
        return -1;
    }

    jint result = JNI_VERSION_1_4;

    return result;
}
