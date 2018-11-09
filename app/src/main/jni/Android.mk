LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := color_converter.c \
                   color_convert_jni.c

LOCAL_C_INCLUDES := $(LOCAL_PATH)/ \

LOCAL_MODULE := color_converter

LOCAL_LDLIBS := -llog -ljnigraphics -landroid

include $(BUILD_SHARED_LIBRARY)