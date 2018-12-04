//
// Created by fengyin on 17-11-10.
//

#ifndef MODULE_ARGUMENTS_H
#define MODULE_ARGUMENTS_H

#include <stdint.h>

typedef struct CodecParams {
    uint16_t width;
    uint16_t height;
    uint16_t frameRate;
    uint32_t colorFormat;
    uint32_t bitRate;
} CodecParams;


typedef struct CodecOutputFile {
    char path[128];
    uint64_t duration;
} CodecOutputFile;

static const char *VIDEO_MIME = "video/avc";

#endif //MODULE_ARGUMENTS_H
