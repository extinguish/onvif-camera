//
// Created by fengyin on 17-5-22.
//

#ifndef ADAS_IMEI_ADASUTIL_H
#define ADAS_IMEI_ADASUTIL_H


#include <string.h>

class AdasUtil {
public:

    static int yv12ToNv21(const unsigned char *yv12,
                          const int width,
                          const int height,
                          unsigned char *nv21);

    static int yv12Scale(const unsigned char *yv12,
                         const int width,
                         const int height,
                         unsigned char *scaledbuf,
                         const int scaleWidth,
                         const int scaleHeight);

    static int yv12normalScaleBox(const unsigned char *yv12,
                                  const int width,
                                  const int height,
                                  unsigned char *scaledbuf,
                                  const int scaleWidth,
                                  const int scaleHeight);

    static int yv12normalScaleLinear(const unsigned char *yv12,
                                     const int width,
                                     const int height,
                                     unsigned char *scaledbuf,
                                     const int scaleWidth,
                                     const int scaleHeight);

//    static int nv21Scale(unsigned char *nv12,
//                         const int width,
//                         const int height,
//                         unsigned char *u_src,
//                         unsigned char *v_src,
//                         unsigned char *scaledbuf,
//                         const int scaleWidth,
//                         const int scaleHeight);

    static int yv12ToRgb(const unsigned char *yv12,
                         const int width,
                         const int height,
                         unsigned char *rgb,
                         const int rgb_width,
                         const int rgb_height);

    static int yv12ToI420(unsigned char *src, int srcWidth, int srcHeight,
                          unsigned char *dest, int destWidth, int destHeight);

    static long long currentTimeMillis();

};

#endif //ADAS_IMEI_ADASUTIL_H
