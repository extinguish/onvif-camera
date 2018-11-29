LOCAL_PATH := $(call my-dir)

# 这里我们需要单独的设置每一个子目录当中的Android.mk的具体路径，因为我们每引入一个Android.mk
# 都会重新指定一个新的LOCAL_PATH,然后导致后面的Android.mk引入路径错误
USAGE_ENVIRONMENT_TOP_PATH := $(LOCAL_PATH)/../UsageEnvironment
BASIC_USAGE_ENVIRONMENT_TOP_PATH := $(LOCAL_PATH)/../BasicUsageEnvironment
GROUP_SOCK_TOP_PATH := $(LOCAL_PATH)/../groupsock
LIVE_MEDIA_TOP_PATH := $(LOCAL_PATH)/../liveMedia
MEDIA_SERVER_TOP_PATH := $(LOCAL_PATH)/../mediaServer
# PROXY_SERVER_TOP_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := media_server

# 我们在编译live555时，并没有单独的编译usage_environment, basic_usage_environment, group_sock, media_server
# 这些模块，而是整体进行编译，主要是因为这四个模块之间互相进行应用，根本不存在一个完全独立的模块，这就导致了必须将他们一起编译
# 当然了，在每个目录下都有一个一个makefile,目前还不知道每隔目录下面的makefile的具体含义是什么
LOCAL_SRC_FILES := $(USAGE_ENVIRONMENT_TOP_PATH)/HashTable.cpp \
                   $(USAGE_ENVIRONMENT_TOP_PATH)/strDup.cpp \
                   $(USAGE_ENVIRONMENT_TOP_PATH)/UsageEnvironment.cpp \
                   $(BASIC_USAGE_ENVIRONMENT_TOP_PATH)/BasicHashTable.cpp \
                   $(BASIC_USAGE_ENVIRONMENT_TOP_PATH)/BasicTaskScheduler.cpp \
                   $(BASIC_USAGE_ENVIRONMENT_TOP_PATH)/BasicTaskScheduler0.cpp \
                   $(BASIC_USAGE_ENVIRONMENT_TOP_PATH)/BasicUsageEnvironment.cpp \
                   $(BASIC_USAGE_ENVIRONMENT_TOP_PATH)/BasicUsageEnvironment0.cpp \
                   $(BASIC_USAGE_ENVIRONMENT_TOP_PATH)/DelayQueue.cpp \
                   $(GROUP_SOCK_TOP_PATH)/GroupEId.cpp \
                   $(GROUP_SOCK_TOP_PATH)/Groupsock.cpp \
                   $(GROUP_SOCK_TOP_PATH)/GroupsockHelper.cpp \
                   $(GROUP_SOCK_TOP_PATH)/inet.c \
                   $(GROUP_SOCK_TOP_PATH)/IOHandlers.cpp \
                   $(GROUP_SOCK_TOP_PATH)/NetAddress.cpp \
                   $(GROUP_SOCK_TOP_PATH)/NetInterface.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/AC3AudioFileServerMediaSubsession.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/InputFile.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/MPEG4GenericRTPSource.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/AC3AudioRTPSink.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/JPEGVideoRTPSink.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/MPEG4LATMAudioRTPSink.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/AC3AudioRTPSource.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/JPEGVideoRTPSource.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/MPEG4LATMAudioRTPSource.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/AC3AudioStreamFramer.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/JPEGVideoSource.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/MPEG4VideoFileServerMediaSubsession.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/ADTSAudioFileServerMediaSubsession.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/Locale.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/MPEG4VideoStreamDiscreteFramer.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/ADTSAudioFileSource.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/MPEG4VideoStreamFramer.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/AMRAudioFileServerMediaSubsession.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/MPEGVideoStreamFramer.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/AMRAudioFileSink.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/MPEGVideoStreamParser.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/AMRAudioFileSource.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/MatroskaDemuxedTrack.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/AMRAudioRTPSink.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/MultiFramedRTPSink.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/AMRAudioRTPSource.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/MatroskaFile.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/MultiFramedRTPSource.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/AMRAudioSource.cpp    \
                   $(LIVE_MEDIA_TOP_PATH)/MatroskaFileParser.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/OggDemuxedTrack.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/AudioInputDevice.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/MatroskaFileServerDemux.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/OggFile.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/AudioRTPSink.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/MatroskaFileServerMediaSubsession.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/OggFileParser.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/AVIFileSink.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/Base64.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/Media.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/OggFileServerDemux.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/BasicUDPSink.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/MediaSession.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/OggFileServerMediaSubsession.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/BasicUDPSource.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/MediaSink.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/BitVector.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/MediaSource.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/OggFileSink.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/ByteStreamFileSource.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/MP3ADU.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/OnDemandServerMediaSubsession.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/ByteStreamMemoryBufferSource.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/MP3ADUdescriptor.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/ourMD5.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/ByteStreamMultiFileSource.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/OutputFile.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/MP3ADUinterleaving.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/PassiveServerMediaSubsession.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/MP3ADURTPSink.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/ProxyServerMediaSession.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/DeviceSource.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/MP3ADURTPSource.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/QCELPAudioRTPSource.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/DigestAuthentication.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/MP3ADUTranscoder.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/QuickTimeFileSink.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/DVVideoFileServerMediaSubsession.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/MP3AudioFileServerMediaSubsession.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/QuickTimeGenericRTPSource.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/DVVideoRTPSink.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/MP3AudioMatroskaFileServerMediaSubsession.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/RawVideoRTPSink.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/DVVideoRTPSource.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/RawVideoRTPSource.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/DVVideoStreamFramer.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/MP3FileSource.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/RTCP.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/EBMLNumber.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/MP3Internals.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/rtcp_from_spec.c \
                   $(LIVE_MEDIA_TOP_PATH)/FileServerMediaSubsession.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/MP3InternalsHuffman.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/RTPInterface.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/FileSink.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/RTPSink.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/FramedFileSource.cpp    \
                   $(LIVE_MEDIA_TOP_PATH)/MP3InternalsHuffmanTable.cpp    \
                   $(LIVE_MEDIA_TOP_PATH)/RTPSource.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/FramedFilter.cpp    \
                   $(LIVE_MEDIA_TOP_PATH)/MP3StreamState.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/RTSPClient.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/FramedSource.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/RTSPCommon.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/GenericMediaServer.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/MP3Transcoder.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/RTSPRegisterSender.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/GSMAudioRTPSink.cpp    \
                   $(LIVE_MEDIA_TOP_PATH)/MPEG1or2AudioRTPSink.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/RTSPServer.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/H261VideoRTPSource.cpp    \
                   $(LIVE_MEDIA_TOP_PATH)/MPEG1or2AudioRTPSource.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/RTSPServerRegister.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/H263plusVideoFileServerMediaSubsession.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/MPEG1or2AudioStreamFramer.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/RTSPServerSupportingHTTPStreaming.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/H263plusVideoRTPSink.cpp    \
                   $(LIVE_MEDIA_TOP_PATH)/MPEG1or2Demux.cpp    \
                   $(LIVE_MEDIA_TOP_PATH)/ServerMediaSession.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/H263plusVideoRTPSource.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/MPEG1or2DemuxedElementaryStream.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/SimpleRTPSink.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/H263plusVideoStreamFramer.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/MPEG1or2DemuxedServerMediaSubsession.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/SimpleRTPSource.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/H263plusVideoStreamParser.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/MPEG1or2FileServerDemux.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/SIPClient.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/MPEG1or2VideoFileServerMediaSubsession.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/StreamParser.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/H264or5VideoFileSink.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/MPEG1or2VideoRTPSink.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/H264or5VideoRTPSink.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/MPEG1or2VideoRTPSource.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/StreamReplicator.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/H264or5VideoStreamDiscreteFramer.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/MPEG1or2VideoStreamDiscreteFramer.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/T140TextRTPSink.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/H264or5VideoStreamFramer.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/MPEG1or2VideoStreamFramer.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/TCPStreamSink.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/H264VideoFileServerMediaSubsession.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/MPEG2IndexFromTransportStream.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/TextRTPSink.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/H264VideoFileSink.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/MPEG2TransportFileServerMediaSubsession.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/TheoraVideoRTPSink.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/H264VideoRTPSink.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/MPEG2TransportStreamAccumulator.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/TheoraVideoRTPSource.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/H264VideoRTPSource.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/MPEG2TransportStreamFramer.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/uLawAudioFilter.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/H264VideoStreamDiscreteFramer.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/MPEG2TransportStreamFromESSource.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/VideoRTPSink.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/H264VideoStreamFramer.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/MPEG2TransportStreamFromPESSource.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/VorbisAudioRTPSink.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/H265VideoFileServerMediaSubsession.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/MPEG2TransportStreamIndexFile.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/VorbisAudioRTPSource.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/H265VideoFileSink.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/MPEG2TransportStreamMultiplexor.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/VP8VideoRTPSink.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/H265VideoRTPSink.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/MPEG2TransportStreamTrickModeFilter.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/VP8VideoRTPSource.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/H265VideoRTPSource.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/MPEG2TransportUDPServerMediaSubsession.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/VP9VideoRTPSink.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/H265VideoStreamDiscreteFramer.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/MPEG4ESVideoRTPSink.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/VP9VideoRTPSource.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/H265VideoStreamFramer.cpp   \
                   $(LIVE_MEDIA_TOP_PATH)/MPEG4ESVideoRTPSource.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/WAVAudioFileServerMediaSubsession.cpp \
                   $(LIVE_MEDIA_TOP_PATH)/MPEG4GenericRTPSink.cpp  \
                   $(LIVE_MEDIA_TOP_PATH)/WAVAudioFileSource.cpp \
                   $(MEDIA_SERVER_TOP_PATH)/DynamicRTSPServer.cpp \
                   $(MEDIA_SERVER_TOP_PATH)/live555MediaServer.cpp \


LOCAL_C_INCLUDES := $(BASIC_USAGE_ENVIRONMENT_TOP_PATH)/include \
                    $(USAGE_ENVIRONMENT_TOP_PATH)/include \
                    $(GROUP_SOCK_TOP_PATH)/include \
                    $(LIVE_MEDIA_TOP_PATH)/ \
                    $(LIVE_MEDIA_TOP_PATH)/include \


LOCAL_SHARED_LIBRARY := media_server

include $(BUILD_SHARED_LIBRARY)