SHARE_BUF_RTSP_SERVER_TOP_PATH = $(call my-dir)

# include $(CLEAR_VARS)

$(info "the rtsp server work path are " $(SHARE_BUF_RTSP_SERVER_TOP_PATH))

LOCAL_MODULE := ipcamera_rtsp_server

LOCAL_SRC_FILES := $(SHARE_BUF_RTSP_SERVER_TOP_PATH)/HTTPServer.cpp \
                   $(SHARE_BUF_RTSP_SERVER_TOP_PATH)/DeviceSource.cpp \
                   $(SHARE_BUF_RTSP_SERVER_TOP_PATH)/H264_V4l2DeviceSource.cpp \
                   $(SHARE_BUF_RTSP_SERVER_TOP_PATH)/ServerMediaSubsession.cpp \
                   $(SHARE_BUF_RTSP_SERVER_TOP_PATH)/IPCameraRtspServer.cpp \
                   $(SHARE_BUF_RTSP_SERVER_TOP_PATH)/UnicastServerMediaSubsession.cpp \

LOCAL_C_INCLUDES := $(SHARE_BUF_RTSP_SERVER_TOP_PATH)/../live_server/UsageEnvironment/include \
                    $(SHARE_BUF_RTSP_SERVER_TOP_PATH)/../live_server/BasicUsageEnvironment/include \
                    $(SHARE_BUF_RTSP_SERVER_TOP_PATH)/../live_server/groupsock/include \
                    $(SHARE_BUF_RTSP_SERVER_TOP_PATH)/../live_server/liveMedia/include \
                    $(SHARE_BUF_RTSP_SERVER_TOP_PATH)/../live_server/mediaServer/include \
                    $(SHARE_BUF_RTSP_SERVER_TOP_PATH)/include \
                    $(SHARE_BUF_RTSP_SERVER_TOP_PATH)/../simple_utils.h \
                    $(SHARE_BUF_RTSP_SERVER_TOP_PATH)/../h264_handle/IH264DataListener.h \


# 我们需要依赖live555 media_server库
# 还需要依赖h264_encoder库，依赖h264_encoder主要是为了接收编码好的数据
# h264_encoder对于我们来说就是一个数据源
LOCAL_SHARED_LIBRARIES := media_server h264_encoder

include $(BUILD_SHARED_LIBRARY)





