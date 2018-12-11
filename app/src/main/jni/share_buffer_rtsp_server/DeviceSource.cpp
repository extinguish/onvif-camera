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
                            bool useThread) {
    V4L2DeviceSource *source = NULL;
    source = new V4L2DeviceSource(env, queueSize, useThread);
    return source;
}

/**
 * 我们支持两种方式来获取编码好的视频数据
 * 第一种是使用thread,第二种是使用live555 mediaServer内部提供的
 * TaskScheduler来进行管理.
 */
V4L2DeviceSource::V4L2DeviceSource(UsageEnvironment &env,
                                   unsigned int queueSize,
                                   bool useThread)
        : FramedSource(env),
          m_in("in"),
          m_out("out"),
          m_queueSize(queueSize) {
    m_eventTriggerId = envir().taskScheduler().createEventTrigger(
            V4L2DeviceSource::deliverFrameStub);
    memset(&m_thid, 0, sizeof(m_thid));
    memset(&m_mutex, 0, sizeof(m_mutex));
    this->useThread = useThread;
    LOGD_T(LOG_TAG, "Construct the V4L2DeviceSource instance");
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
    bool stop = false;

    LOGD_T(LOG_TAG, "begin thread");
    while (!stop) {
        std::shared_ptr<RawH264FrameData *> ptr = this->raw_h264_frame_queue->wait_and_pop();
        RawH264FrameData *encoded_frame = *ptr.get();
        if (encoded_frame != NULL) {
            getNextFrame(reinterpret_cast<char *>(encoded_frame->raw_frame), encoded_frame->size);
            delete encoded_frame;
        } else {
            stop = true;
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
                   "deliver frame time stamp of %ld.%ld \n frame size are %d \t diff %d, queue size %d",
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

// TODO: 在构造函数当中，我们有两种操作实现策略，第一种是直接useThread
// TODO: 第二种就是借助于envir().taskScheduler()来进行调度，这两种策略是完全平等的
// TODO: 因为他们最终都会调用到getNextFrame()方法来进行视频帧的处理
//void V4L2DeviceSource::incomingPacketHandler() {
//    if (this->getNextFrame() <= 0) {
//        // handleClosure是FramedSource类提供的方法，当我们调用getNextFrame()返回值<=0
//        // 即代表nextFrame获取失败了
//        handleClosure(this);
//    }
//}

// getNextFrame()方法被两个地方调用，第一个是DeviceSource#thread()方法;
// 第二个是DeviceSource#incomingPacketHandler()方法;
// 从device当中读取出一帧数据
int V4L2DeviceSource::getNextFrame(char *frame_data, int frame_size) {
    timeval ref;
    gettimeofday(&ref, NULL);

    if (frame_size < 0) {
        LOGD_T(LOG_TAG, "V4L2DeviceSource::getNextFrame errno: %d, %s", errno, strerror(errno));
    } else if (frame_size == 0) {
        LOGD_T(LOG_TAG, "V4L2DeviceSource::getNextFrame no data errno: %d, %s", errno,
               strerror(errno));
    } else {
        // 读取数据成功
        timeval tv;
        gettimeofday(&tv, NULL);
        timeval diff;
        timersub(&tv, &ref, &diff);
        m_in.notify(tv.tv_sec, frame_size);
        LOGD_T(LOG_TAG, "getNextFrame\ttimestamp: %ld.%ld \tsize: %d", ref.tv_sec, ref.tv_usec,
               frame_size);
        processFrame(frame_data, frame_size, ref);
    }
    return frame_size;
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

/**
 * setupRawH264FrameQueue(long)是整个读取流的起点，我们从这里开始对编好码的数据的处理
 */
void V4L2DeviceSource::setupRawH264FrameQueue(long rawH264FrameQueueObjAddress) {
    // 我们只有在确保raw_h264_frame_queue可用之后，才能从里面读取数据
    this->raw_h264_frame_queue = reinterpret_cast<ThreadQueue<RawH264FrameData *>*>(rawH264FrameQueueObjAddress);

    if (this->raw_h264_frame_queue == nullptr) {
        LOGERR(LOG_TAG, "fail to get the raw_h264_frame_queue, so we cannot send out the frame data");
        return;
    }

    if (this->useThread) {
        // 使用一个单独的thread从视频设备当中读取视频流数据
        pthread_mutex_init(&m_mutex, NULL);
        pthread_create(&m_thid, NULL, threadStub, this);
    }
//    else {
//        // 不使用单独的thread来从视频设备当中读取视频流数据
//        // 而是通过使用UsageEnvironment当中的TaskScheduler来协调规划
//        // 读取视频流的任务
//        envir().taskScheduler()
//                .turnOnBackgroundReadHandling(fd,
//                                              V4L2DeviceSource::incomingPacketHandlerStub,
//                                              this);
//    }
}


	
