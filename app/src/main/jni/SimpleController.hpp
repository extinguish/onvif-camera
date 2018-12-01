#ifndef __SIMPLE_CONTROLLER
#define __SIMPLE_CONTROLLER

#include <string>
#include "simple_utils.h"
#include "h264_handle/KyEncoder.h"

#define TAG_C "rtmp_service"

class SimpleController {
public:

    virtual void
    init(const uint16_t encode_width, const uint16_t encode_height,
         const uint32_t connect_time_out);

    ~SimpleController();

    virtual KyEncoder *getKyEncoder();

    virtual H264DataListenerImpl *getH264DataListenerImpl();

private:
    KyEncoder *kyEncoder;
    H264DataListenerImpl *h264DataListenerImpl;
    Mutex mutex;
};

#endif