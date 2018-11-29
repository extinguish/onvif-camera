LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := live_proxy_server

LOCAL_SRC_FILES := live555ProxyServer.cpp

LOCAL_C_INCLUDES := $(LOCAL_PATH)/UsageEnvironment/include \
                    $(LOCAL_PATH)/BasicUsageEnvironment/include \
                    $(LOCAL_PATH)/groupsock/include \
                    $(LOCAL_PATH)/liveMedia/include \


LOCAL_SHARED_LIBRARY := usage_environment \
                        basic_usage_environment \
                        group_sock \
                        live_media \


include $(BUILD_SHARED_LIBRARY)