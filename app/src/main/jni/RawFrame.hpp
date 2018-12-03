// 用于保存编码后的h264数据帧

#ifndef RAW_H264_FRMAE
#define RAW_H264_FRMAE

#define BYTE uint8_t

class RawH264FrameData {
public:
    BYTE *raw_frame;

    int32_t size;

    RawH264FrameData(BYTE *raw_frame, int32_t frame_size) : raw_frame(raw_frame),
                                                            size(frame_size) {}

    ~RawH264FrameData() {
        if (raw_frame != nullptr) {
            delete raw_frame;
        }
    }
};

#endif
