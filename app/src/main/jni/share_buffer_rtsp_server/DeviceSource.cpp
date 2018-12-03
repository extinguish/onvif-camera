/* ---------------------------------------------------------------------------
** This software is in the public domain, furnished "as is", without technical
** support, and with no warranty, express or implied, as to its usefulness for
** any purpose.
**
** v4l2DeviceSource.cpp
** 
** V4L2 Live555 source 
**
** -------------------------------------------------------------------------*/

#include <fcntl.h>
#include <iomanip>
#include <sstream>

// project
#include "include/DeviceSource.h"

#define LOG_TAG "device_source"

// 用于统计推流过程当中的实时信息
// ---------------------------------
// V4L2 FramedSource Stats
// ---------------------------------
int V4L2DeviceSource::Stats::notify(int tv_sec, int framesize) {
    m_fps++;
    m_size += framesize;
    if (tv_sec != m_fps_sec) {
        LOGD_T(LOG_TAG, "tv_sec : %d, fps %d, band width %d kbps", tv_sec, m_fps, (m_size / 128));
        m_fps_sec = tv_sec;
        m_fps = 0;
        m_size = 0;
    }
    return m_fps;
}

// ---------------------------------
// V4L2 FramedSource
// ---------------------------------
V4L2DeviceSource *
V4L2DeviceSource::createNew(UsageEnvironment &env, unsigned int queueSize,
                            bool useThread, int fd) {
    V4L2DeviceSource *source = NULL;
    source = new V4L2DeviceSource(env, queueSize, useThread, fd);
    return source;
}

/**
 * 我们支持两种方式来获取编码好的视频数据
 * 第一种是使用thread,第二种是使用live555 mediaServer内部提供的
 * TaskScheduler来进行管理.
 */
V4L2DeviceSource::V4L2DeviceSource(UsageEnvironment &env,
                                   unsigned int queueSize,
                                   bool useThread,
                                   int fd)
        : FramedSource(env),
          m_in("in"),
          m_out("out"),
          m_queueSize(queueSize) {
    this->videoDataFd = fd;
    m_eventTriggerId = envir().taskScheduler().createEventTrigger(
            V4L2DeviceSource::deliverFrameStub);
    memset(&m_thid, 0, sizeof(m_thid));
    memset(&m_mutex, 0, sizeof(m_mutex));
    // 判断是否使用单独的thread来从视频设备当中读取视频流数据
    if (useThread) {
        // 使用一个单独的thread从视频设备当中读取视频流数据
        pthread_mutex_init(&m_mutex, NULL);
        pthread_create(&m_thid, NULL, threadStub, this);
    } else {
        // 不使用单独的thread来从视频设备当中读取视频流数据
        // 而是通过使用UsageEnvironment当中的TaskScheduler来协调规划
        // 读取视频流的任务
        envir().taskScheduler()
                .turnOnBackgroundReadHandling(fd,
                                              V4L2DeviceSource::incomingPacketHandlerStub,
                                              this);
    }
}

// Destructor
V4L2DeviceSource::~V4L2DeviceSource() {
    envir().taskScheduler().deleteEventTrigger(m_eventTriggerId);
    pthread_join(m_thid, NULL);
    pthread_mutex_destroy(&m_mutex);
}

// 由DeviceSource#threadStub()方法调用
// 而DeviceSource#threadStub()方法则在DeviceSource的构造函数当中被调用
// 即只要创建了DeviceSource的实例，就会自动开启当前的这个thread
// thread mainloop
void *V4L2DeviceSource::thread() {
    // 我们需要再这里获取到对应的视频流信息
    // 即我们需要在这里同H264DataListenerImpl#onVideoFrame()方法结合起来
    // H264DataListenerImpl#onVideoFrame()方法会由编码好的数据，然后我们将编码好的数据
    // 就可以直接交给当前的DeviceSource(DeviceSource内部会调用H264_V4l2DeviceSource,
    // 最终的H264_V4l2DeviceSource本身会负责将数据进行处理，包括解析出其中的sps和pps,然后
    // 交给RTSPServerMediaSession进行处理)
    // 理想情况下，我们应该通过使用两个interface将DeviceSource和IH264DataListener组合起来
    // 然后传输视频流数据，但是还没有想到比较好的方式，所以这里使用的是一种简化的方式，即
    // 直接通过文件描述符来进行操作
    // TODO: 可以尝试将DeviceSource和IH264DataListener组合起来,省略写文件的步骤
    if (this->videoDataFd != -1) {
        LOGERR(LOG_TAG,
               "the video data fd are -1, do not have data to feed to RTSP server, just return");
        return NULL;
    }

    int stop = 0;
    fd_set fdset;
    FD_ZERO(&fdset);
    timeval tv;

    LOGD_T(LOG_TAG, "begin thread");
    while (!stop) {
        // 以下的fd是我们的视频流来源的文件描述符
        int fd = this->videoDataFd;
        FD_SET(fd, &fdset);
        tv.tv_sec = 1;
        tv.tv_usec = 0;
        int ret = select(fd + 1, &fdset, NULL, NULL, &tv);
        if (ret == 1) {
            if (FD_ISSET(fd, &fdset)) {
                if (this->getNextFrame() <= 0) {
                    LOGD_T(LOG_TAG, "error happened while get next frame %s", strerror(errno));
                    stop = 1;
                }
            }
        } else if (ret == -1) {
            LOGD_T(LOG_TAG, "stop the thread caused by %s", strerror(errno));
            stop = 1;
        }
    }
    LOGD_T(LOG_TAG, "end thread");
    return NULL;
}

/// doGetNextFrame()方法是继承自FramedSource的方法
/// 这个方法本身是由liveMedia内部调用的
// getting FrameSource callback
void V4L2DeviceSource::doGetNextFrame() {
    deliverFrame();
}

// stopping FrameSource callback
void V4L2DeviceSource::doStopGettingFrames() {
    LOGD_T(LOG_TAG, "stop getting frames");
    FramedSource::doStopGettingFrames();
}

/// 由DeviceSource#doGetNextFrame()调用
// sink相当于rtsp server的数据接收端
// 与sink相对应的就是source端了
// deliver frame to the sink
void V4L2DeviceSource::deliverFrame() {
    if (isCurrentlyAwaitingData()) {
        fDurationInMicroseconds = 0;
        fFrameSize = 0;

        pthread_mutex_lock(&m_mutex);
        if (m_captureQueue.empty()) {
            LOGD_T(LOG_TAG, "the queue is empty");
        } else {
            timeval curTime;
            gettimeofday(&curTime, NULL);
            Frame *frame = m_captureQueue.front();
            m_captureQueue.pop_front();

            m_out.notify(curTime.tv_sec, frame->m_size);
            if (frame->m_size > fMaxSize) {
                fFrameSize = fMaxSize;
                fNumTruncatedBytes = frame->m_size - fMaxSize;
            } else {
                fFrameSize = frame->m_size;
            }
            timeval diff;
            timersub(&curTime, &(frame->m_timestamp), &diff);

            int diffTime = (diff.tv_sec * 1000 + diff.tv_usec / 1000);
            LOGD_T(LOG_TAG,
                   "deliver frame time stamp of %ld.%d \n frame size are %d \t diff %d, queue size %d",
                   curTime.tv_sec, curTime.tv_usec, fFrameSize,
                   diffTime,
                   m_captureQueue.size());

            fPresentationTime = frame->m_timestamp;
            memcpy(fTo, frame->m_buffer, fFrameSize);
            delete frame;
        }
        pthread_mutex_unlock(&m_mutex);

        if (fFrameSize > 0) {
            // send Frame to the consumer
            FramedSource::afterGetting(this);
        }
    }
}

// FrameSource callback on read event
void V4L2DeviceSource::incomingPacketHandler() {
    if (this->getNextFrame() <= 0) {
        // handleClosure是FramedSource类提供的方法，当我们调用getNextFrame()返回值<=0
        // 即代表nextFrame获取失败了
        handleClosure(this);
    }
}

/// getNextFrame()方法被两个地方调用，第一个是DeviceSource#thread()方法;
/// 第二个是DeviceSource#incomingPacketHandler()方法;
// 从device当中读取出一帧数据
// read from device
int V4L2DeviceSource::getNextFrame() {
    timeval ref;
    gettimeofday(&ref, NULL);
    // FIXME: guoshichao 这里的bufferSize不应该是固定大小的，应该根据我们写入的数据的大小来进行评估
    // FIXME: guoshichao 这里的bufferSize是否可以由FD_SELECT那里获得???
    const int bufferSize = 1000;
    char buffer[bufferSize];
    int frameSize = readFrameFromFile(buffer, bufferSize);

    if (frameSize < 0) {
        LOGD_T(LOG_TAG, "V4L2DeviceSource::getNextFrame errno: %d, %s", errno, strerror(errno));
    } else if (frameSize == 0) {
        LOGD_T(LOG_TAG, "V4L2DeviceSource::getNextFrame no data errno: %d, %s", errno,
               strerror(errno));
    } else {
        // 读取数据成功
        timeval tv;
        gettimeofday(&tv, NULL);
        timeval diff;
        timersub(&tv, &ref, &diff);
        m_in.notify(tv.tv_sec, frameSize);
        LOGD_T(LOG_TAG, "getNextFrame\ttimestamp: %ld.%ld \tsize: %d", ref.tv_sec, ref.tv_usec,
               frameSize);
        processFrame(buffer, frameSize, ref);
    }
    return frameSize;
}


/// 由V4L2DeviceSource::getNextFrame()方法调用
void V4L2DeviceSource::processFrame(char *frame, int frameSize, const timeval &ref) {
    timeval tv;
    gettimeofday(&tv, NULL);
    timeval diff;
    timersub(&tv, &ref, &diff);

    std::list<std::pair<unsigned char *, size_t> > frameList =
            this->splitFrames((unsigned char *) frame, frameSize);
    // 逐个处理frameList当中的frame
    while (!frameList.empty()) {
        std::pair<unsigned char *, size_t> &frame = frameList.front();
        size_t size = frame.second;
        char *buf = new char[size];
        memcpy(buf, frame.first, size);
        queueFrame(buf, size, ref);

        int diffTime = diff.tv_sec * 1000 + diff.tv_usec / 1000;
        LOGD_T(LOG_TAG, "queueFrame with timestamp of %ld.%ld, size of %d, diff %d ms",
               ref.tv_sec, ref.tv_usec, size, diffTime);
        frameList.pop_front();
    }
}

// 由V4L2DeviceSource::processFrame()方法调用
// queueFrame()当中接受的frame是来自于splitFrame()方法解析过的frame数据
// post a frame to fifo
void V4L2DeviceSource::queueFrame(char *frame, int frameSize, const timeval &tv) {
    pthread_mutex_lock(&m_mutex);
    while (m_captureQueue.size() >= m_queueSize) {
        LOGD_T(LOG_TAG, "Queue full size drop frame size: %d", (int) m_captureQueue.size());
        delete m_captureQueue.front();
        m_captureQueue.pop_front();
    }
    m_captureQueue.push_back(new Frame(frame, frameSize, tv));
    pthread_mutex_unlock(&m_mutex);

    // 通知UsageEnvironment当中的TaskScheduler有新数据到来，然后
    // TaskScheduler会同RtspServer进行协调
    // 这步就认为我们将数据推送到rtsp服务器当中
    // post an event to ask to deliver the frame
    envir().taskScheduler().triggerEvent(m_eventTriggerId, this);
}

// 该方法由DeviceSource#processFrame()方法调用
// 再运行时，splitFrames()并不一定是这里的实现，而有可能是
// H264_V4L2DeviceSource#splitFrames()方法的实现.
// 对于原始的V4L2DeviceSource实现来说，是直接将每一帧的数据都直接放到frameList当中
// 不进行处理
// 对于H264_V4l2DeviceSource来说，会对这里的每一个frame解析一下，然后再处理，
// 即解析出其中的sps和pps
// split packet in frames					
std::list<std::pair<unsigned char *, size_t> > V4L2DeviceSource::splitFrames(unsigned char *frame,
                                                                             unsigned frameSize) {
    std::list<std::pair<unsigned char *, size_t> > frameList;
    if (frame != NULL) {
        frameList.push_back(std::pair<unsigned char *, size_t>(frame, frameSize));
    }
    return frameList;
}

size_t V4L2DeviceSource::readFrameFromFile(char *buffer, size_t bufferSize) {
    // TODO: 读取数据
    size_t readSize = 0;
    memset(&buffer, 0, sizeof(buffer));


    return 0;
}


	
