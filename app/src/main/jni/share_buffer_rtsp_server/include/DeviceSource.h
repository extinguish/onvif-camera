/* ---------------------------------------------------------------------------
** This software is in the public domain, furnished "as is", without technical
** support, and with no warranty, express or implied, as to its usefulness for
** any purpose.
**
** V4l2DeviceSource.h
** 
** V4L2 live555 source 
**
** -------------------------------------------------------------------------*/


#ifndef DEVICE_SOURCE
#define DEVICE_SOURCE

#include <string>
#include <list>
#include <iostream>
#include <iomanip>

// live555
#include "../../live_server/liveMedia/include/FramedSource.hh"

#include "../../simple_utils.h"

// 在我们参考的v4l2实现当中，v4l2是从Linux当中的video设备当中读取出原始的视频流数据信息
// 但是我们是不需要
class V4L2DeviceSource : public FramedSource {
public:
    // ---------------------------------
    // Captured frame
    // ---------------------------------
    struct Frame {
        Frame(char *buffer, int size, timeval timestamp) :
                m_buffer(buffer), m_size(size), m_timestamp(timestamp) {};

        Frame(const Frame &);

        Frame &operator=(const Frame &);

        ~Frame() { delete[] m_buffer; };

        char *m_buffer;
        unsigned int m_size;
        timeval m_timestamp;
    };

    // ---------------------------------
    // Compute simple stats
    // ---------------------------------
    class Stats {
    public:
        Stats(const std::string &msg) : m_fps(0), m_fps_sec(0), m_size(0), m_msg(msg) {};

    public:
        int notify(int tv_sec, int framesize);

    protected:
        int m_fps;
        int m_fps_sec;
        int m_size;
        const std::string m_msg;
    };

public:
    // 用于创建V4L2DeviceSource的静态工厂方法
    // 在这里我们使用了DeviceInterface来创建V4L2DeviceSource的实例
    static V4L2DeviceSource *createNew(UsageEnvironment &env,
                                       unsigned int queueSize,
                                       bool useThread, int fd);

    std::string getAuxLine() { return m_auxLine; };

    void setAuxLine(const std::string auxLine) { m_auxLine = auxLine; };

protected:
    /**
     * fd参数就是编码好的数据写入到的文件,即我们要通过rtsp上传的数据的数据源
     * fd的默认值我们设置为-1
     */
    V4L2DeviceSource(UsageEnvironment &env,
                     unsigned int queueSize,
                     bool useThread,
                     int fd = -1);

    virtual ~V4L2DeviceSource();

protected:
    static void *threadStub(void *clientData) {
        return ((V4L2DeviceSource *) clientData)->thread();
    };

    void *thread();

    static void deliverFrameStub(void *clientData) {
        ((V4L2DeviceSource *) clientData)->deliverFrame();
    };

    void deliverFrame();

    static void incomingPacketHandlerStub(void *clientData,
                                          int mask) {
        ((V4L2DeviceSource *) clientData)->incomingPacketHandler();
    };

    void incomingPacketHandler();

    int getNextFrame();

    void processFrame(char *frame, int frameSize, const timeval &ref);

    void queueFrame(char *frame, int frameSize, const timeval &tv);

    /// 将packet切分成frame
    /// 不同的子类当中提供不同的实现
    /// 其中父类V4L2DeviceSource提供了自己的实现
    /// 在H264_V4l2DeviceSource当中也提供了自己的实现
    /// 其中H264_V4L2DeviceSource当中在splitFrames当中定义了关于sps和pps的解析方式.
    /// 具体采用哪一种具体的splitFrames实现，取决于运行时多态的选择
    /// 这也算是DeviceSource的一种设计,根据不同的视频编码格式来采用不同的解析方式
    /// 例如对于h264编码，我们需要解析出sps和pps
    /// 这样有利于我们扩展更多的视频格式
    // split packet in frames
    virtual std::list<std::pair<unsigned char *, size_t> > splitFrames(unsigned char *frame,
                                                                       unsigned frameSize);

    // override FramedSource
    virtual void doGetNextFrame();

    virtual void doStopGettingFrames();

protected:
    std::list<Frame *> m_captureQueue;
    Stats m_in;
    Stats m_out;
    EventTriggerId m_eventTriggerId;
    unsigned int m_queueSize;
    pthread_t m_thid;
    pthread_mutex_t m_mutex;
    std::string m_auxLine;
    int videoDataFd;

private:
    /**
     * 从本地的文件当中读取出视频流数据
     */
    size_t readFrameFromFile(char *buffer, size_t bufferSize);
};

#endif
