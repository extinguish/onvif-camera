LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := h264_encoder

LOCAL_SRC_FILES := AdasUtil.cpp \
                    H264DataListenerImpl.cpp \
                    KyEncoder.cpp \

LOCAL_C_INCLUDES := $(LOCAL_PAT)/../libyuv/include/ \

LOCAL_LDLIBS := -lmediandk -lz

LOCAL_SHARED_LIBRARIES := libyuv

include $(BUILD_SHARED_LIBRARY)