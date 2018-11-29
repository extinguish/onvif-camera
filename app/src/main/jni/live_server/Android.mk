LOCAL_PATH := $(call my-dir)

# 这里我们需要单独的设置每一个子目录当中的Android.mk的具体路径，因为我们每引入一个Android.mk
# 都会重新指定一个新的LOCAL_PATH,然后导致后面的Android.mk引入路径错误
LIVE_TOP_PATH := $(call my-dir)
USAGE_ENVIRONMENT_TOP_PATH := $(call my-dir)
GROUP_SOCK_TOP_PATH := $(call my-dir)
BASIC_USAGE_ENVIRONMENT_TOP_PATH := $(call my-dir)
LIVE_MEDIA_TOP_PATH := $(call my-dir)
MEDIA_SERVER_TOP_PATH := $(call my-dir)

$(info "local path are : " $(LOCAL_PATH))

include $(MEDIA_SERVER_TOP_PATH)/mediaServer/Android.mk

include $(CLEAR_VARS)

LOCAL_MODULE := simple_rtsp_server

LOCAL_SRC_FILES := $(LIVE_TOP_PATH)/SimpleRtspServer.cpp

LOCAL_C_INCLUDES := $(LOCAL_PATH)/UsageEnvironment/include \
                    $(LOCAL_PATH)/BasicUsageEnvironment/include \
                    $(LOCAL_PATH)/groupsock/include \
                    $(LOCAL_PATH)/liveMedia/include \
                    $(LOCAL_PATH)/mediaServer/include \
                    SimpleRtspServer.hpp \

# include the media_server shared library
LOCAL_SHARED_LIBRARY := media_server

include $(BUILD_SHARED_LIBRARY)