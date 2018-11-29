## 关于`share_buffer_rtsp_server`

**share_buffer_rtsp_server**本身主要是从`ShareBuffer`当中读取数据，然后经过`MediaCodec`编码之后，然后通过`RTSP`进行推流发送出去.