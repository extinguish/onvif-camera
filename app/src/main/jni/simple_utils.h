#ifndef SIMPLE_COMMON_H
#define SIMPLE_COMMON_H

#include "android/log.h"

#define TRUE 1
#define FALSE 0

// TODO: guoshichao 这里的ENABLE_DEBUG变量应该由CMake或者Android.mk来控制
// 然后CMake和Android.mk本身由build.gradle来控制，最终由gradle product flavor完成自动化控制
#define ENABLE_DEBUG FALSE

#define DEFAULT_TAG "simple_rtsp_server"

#define LOGD(fmt, ...) \
        if(ENABLE_DEBUG) (void)__android_log_print(ANDROID_LOG_DEBUG, DEFAULT_TAG, fmt, ##__VA_ARGS__)

#define LOGD_T(tag, fmt, ...) \
        if(ENABLE_DEBUG) (void)__android_log_print(ANDROID_LOG_DEBUG, tag, fmt, ##__VA_ARGS__)

// 用于打印关键的错误日志信息，例如网络连接不同这种情况或者网络权限错误.
#define LOGERR(tag, fmt, ...) \
        __android_log_print(ANDROID_LOG_ERROR, tag, fmt, ##__VA_ARGS__)

// INFO级别的日志在项目最终运行时，会保留
#define LOGI_T(tag, fmt, ...) \
        __android_log_print(ANDROID_LOG_DEBUG, tag, fmt, ##__VA_ARGS__)
#endif
