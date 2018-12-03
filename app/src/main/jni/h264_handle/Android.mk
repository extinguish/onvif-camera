LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := h264_encoder

LOCAL_SRC_FILES := AdasUtil.cpp \
                    H264DataListenerImpl.cpp \
                    KyEncoder.cpp \

LOCAL_C_INCLUDES := $(LOCAL_PAT)/../libyuv/include/ \
                    $(LOCAL_PATH)../share_buffer_rtsp_server/include \


LOCAL_LDLIBS := -lmediandk -lz

# 我们在这里依赖ipcamera_rtsp_server, rtsp对于h264_handle来说，就类似于rtmp一样，只是一个普通的数据消费者
# 只是消费的方式不太一样，本质上还是一样的.
LOCAL_SHARED_LIBRARIES := libyuv ipcamera_rtsp_server

include $(BUILD_SHARED_LIBRARY)