//
// Created by fengyin on 17-5-22.
//

#include "include/AdasUtil.h"

#include "../../libyuv/include/libyuv.h"

#include <fcntl.h>
#include <unistd.h>
#include <sys/time.h>
#include <sys/stat.h>

int AdasUtil::yv12ToNv21(const unsigned char *yv12,
                         const int width,
                         const int height,
                         unsigned char *nv21) {
    return libyuv::I420ToNV21(yv12,
                              width,
                              yv12 + width * height,
                              width / 2,
                              yv12 + width * height * 5 / 4,
                              width / 2,
                              nv21,
                              width,
                              nv21 + width * height,
                              width,
                              width,
                              height);
}


int AdasUtil::yv12Scale(const unsigned char *yv12,
                        const int width,
                        const int height,
                        unsigned char *scaledbuf,
                        const int scaleWidth,
                        const int scaleHeight) {
    return libyuv::I420Scale(yv12 + width * (int) (0.1 * height) + (int) (0.1 * width),
                             width,
                             yv12 + width * height + width * (int) (0.025 * height) +
                             (int) (0.05 * width),
                             width / 2,
                             yv12 + width * height * 5 / 4 + width * (int) (0.025 * height) +
                             (int) (0.05 * width),
                             width / 2,
                             static_cast<int>(0.8 * width),
                             static_cast<int>(0.8 * height),
                             scaledbuf,
                             scaleWidth,
                             scaledbuf + scaleWidth * scaleHeight,
                             scaleWidth / 2,
                             scaledbuf + scaleWidth * scaleHeight * 5 / 4,
                             scaleWidth / 2,
                             scaleWidth,
                             scaleHeight,
                             libyuv::kFilterNone);
}

int AdasUtil::yv12normalScaleBox(const unsigned char *yv12,
                                 const int width,
                                 const int height,
                                 unsigned char *scaledbuf,
                                 const int scaleWidth,
                                 const int scaleHeight) {
    return libyuv::I420Scale(yv12,
                             width,
                             yv12 + width * height,
                             width / 2,
                             yv12 + width * height * 5 / 4,
                             width / 2,
                             width,
                             height,
                             scaledbuf,
                             scaleWidth,
                             scaledbuf + scaleWidth * scaleHeight,
                             scaleWidth / 2,
                             scaledbuf + scaleWidth * scaleHeight * 5 / 4,
                             scaleWidth / 2,
                             scaleWidth,
                             scaleHeight,
                             libyuv::kFilterNone);
}


int AdasUtil::yv12normalScaleLinear(const unsigned char *yv12,
                                    const int width,
                                    const int height,
                                    unsigned char *scaledbuf,
                                    const int scaleWidth,
                                    const int scaleHeight) {
    return libyuv::I420Scale(yv12,
                             width,
                             yv12 + width * height,
                             width / 2,
                             yv12 + width * height * 5 / 4,
                             width / 2,
                             width,
                             height,
                             scaledbuf,
                             scaleWidth,
                             scaledbuf + scaleWidth * scaleHeight * 5 / 4,
                             scaleWidth / 2,
                             scaledbuf + scaleWidth * scaleHeight,
                             scaleWidth / 2,
                             scaleWidth,
                             scaleHeight,
                             libyuv::kFilterNone);
}

// 目前我们使用的libyuv没有提供该方法的实现,而且在KyEncoder当中也没有使用到该实现，因此我们暂时将
// 该方法注释掉
//int AdasUtil::nv21Scale(unsigned char *nv21,
//                        const int width,
//                        const int height,
//                        unsigned char *u_src,
//                        unsigned char *v_src,
//                        unsigned char *scaledbuf,
//                        const int scaleWidth,
//                        const int scaleHeight) {
//    SplitUVPlane(nv21 + width * height,
//                 width,
//                 v_src,
//                 width / 2,
//                 u_src,
//                 width / 2,
//                 width / 2,
//                 height / 2);
//
//    memcpy(nv21 + width * height, v_src,
//           width * height / 4);
//
//    memcpy(nv21 + width * height * 5 / 4,
//           u_src, width * height / 4);
//
//    return I420Scale(nv21 + width * (int) (0.1 * height) + (int) (0.1 * width),
//                     width,
//                     nv21 + width * height + width * (int) (0.025 * height) + (int) (0.05 * width),
//                     width / 2,
//                     nv21 + width * height * 5 / 4 + width * (int) (0.025 * height) +
//                     (int) (0.05 * width),
//                     width / 2,
//                     0.8 * width,
//                     0.8 * height,
//                     scaledbuf,
//                     scaleWidth,
//                     scaledbuf + scaleWidth * scaleHeight,
//                     scaleWidth / 2,
//                     scaledbuf + scaleWidth * scaleHeight * 5 / 4,
//                     scaleWidth / 2,
//                     scaleWidth,
//                     scaleHeight,
//                     libyuv::kFilterNone);
//}

int AdasUtil::yv12ToRgb(const unsigned char *yv12,
                        const int width,
                        const int height,
                        unsigned char *rgb,
                        const int rgb_width,
                        const int rgb_height) {
    return libyuv::I420ToRGB24(yv12,
                               width,
                               yv12 + width * height,
                               width / 2,
                               yv12 + width * height * 5 / 4,
                               width / 2,
                               rgb,
                               rgb_width * 3,
                               rgb_width, rgb_height);
}

int AdasUtil::yv12ToI420(unsigned char *src, int srcWidth, int srcHeight,
                         unsigned char *dest, int destWidth, int destHeight) {
    return libyuv::I420Scale(src,
                             srcWidth,
                             src + srcWidth * srcHeight,
                             srcWidth / 2,
                             src + srcWidth * srcHeight * 5 / 4,
                             srcWidth / 2,
                             srcWidth,
                             srcHeight,
                             dest,
                             destWidth,
                             dest + destWidth * destHeight * 5 / 4,
                             destWidth / 2,
                             dest + destWidth * destHeight,
                             destWidth / 2,
                             destWidth,
                             destHeight,
                             libyuv::kFilterNone);
}


long long AdasUtil::currentTimeMillis() {
    timeval now;
    gettimeofday(&now, NULL);
    long long when = now.tv_sec * 1000LL + now.tv_usec / 1000;
    return when;
}
