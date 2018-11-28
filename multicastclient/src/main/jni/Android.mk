LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(LOCAL_PATH)/MulticastListener.c \

LOCAL_C_INCLUDES := $(LOCAL_PATH)/ \


LOCAL_MODULE := multicast_listener

LOCAL_LDLIBS := -llog -landroid

include $(BUILD_SHARED_LIBRARY)






