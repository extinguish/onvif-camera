ROOT_PATH := $(call my-dir)
YUV_PATH := $(call my-dir)
H264_HANDLE_PATH := $(call my-dir)
LIVE_SERVER_PATH := $(call my-dir)
SHARE_BUF_RTSP_SERVER := $(call my-dir)

$(info "the yuv path are :" $(YUV_PATH))

include $(YUV_PATH)/libyuv/Android.mk

# h264_handle和ipcamera_rtsp_server两个模块合并成了一个模块
# $(info "the h264_handle path are " $(H264_HANDLE_PATH))
# include $(H264_HANDLE_PATH)/h264_handle/Android.mk

$(info "the live_server path are " $(LIVE_SERVER_PATH))
include $(LIVE_SERVER_PATH)/live_server/Android.mk

$(info "the share buffer rtsp server path are " $(SHARE_BUF_RTSP_SERVER))
include $(SHARE_BUF_RTSP_SERVER)/share_buffer_rtsp_server/Android.mk

##################################################
# CLEAR_VARS will not clear LOCAL_PATH, but other variables will be cleared.
# but other module may define the LOCAL_PATH again
include $(CLEAR_VARS)

$(info "-->current working path are :" $(ROOT_PATH) "<--")

# 然后就是ipcamera_rtsp_server的控制入口
LOCAL_SRC_FILES := $(ROOT_PATH)/ky_encoder_jni.cpp \
                   $(ROOT_PATH)/SimpleController.cpp \


LOCAL_C_INCLUDES := $(ROOT_PATH)/ \
                    $(ROOT_PATH)/../share_buffer_rtsp_server/include \
                    $(ROOT_PATH)/../h264_handle/ \


# Java层加载的话，只需要加载这里的adas_ipamera就可以了
LOCAL_MODULE := adas_ipcamera

LOCAL_SHARED_LIBRARIES := ipcamera_rtsp_server

# mediandk库本身需要在Android-21以上的版本当中运行,因此我们需要确保编译时，Application.mk
# 当中的api >= 21
LOCAL_LDLIBS := -lmediandk -llog -ljnigraphics -landroid

include $(BUILD_SHARED_LIBRARY)


