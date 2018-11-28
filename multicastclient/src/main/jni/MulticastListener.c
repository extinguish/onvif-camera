#include <stdio.h>
#include <stdlib.h>
#include <jni.h>

#include "simple_log.h"

#define JAVA_CLASS_NAME "com/adasplus/multicast_client"

static void listen_multicast_packet() {

}


static JNINativeMethod gMethods[] = {
        // public native void listenMulticastPacket();
        {"listenMulticastPacket", "()V", (void *) listen_multicast_packet}
};


static int
registerNativeMethods(JNIEnv *env, const char *class_name, JNINativeMethod *native_method,
                      int method_num) {
    jclass clazz;
    clazz = (*env)->FindClass(env, class_name);

    if (clazz == NULL) {
        return JNI_FALSE;
    }

    if (((*env)->RegisterNatives(env, clazz, gMethods, method_num)) < 0) {
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

static int registerAllNativeMethods(JNIEnv *env) {
    if (!registerNativeMethods(env, JAVA_CLASS_NAME, gMethods,
                               sizeof(gMethods) / sizeof(gMethods[0]))) {
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {



    return JNI_TRUE;

}









