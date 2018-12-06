## 关于`share_buffer_rtsp_server`

**share_buffer_rtsp_server**本身主要是从`ShareBuffer`当中读取数据，然后经过`MediaCodec`编码之后，然后通过`RTSP`进行推流发送出去.


----------------------------------------------------

`live555`的控制流程:

在`live555`当中，`RTSPServer`本身处理来自`client`的数据请求，然后`RTSPServer`将收到的客户请求进行解析，
对于合法的请求，`RTSPServer`会将状态同步到`ServerMediaSession`当中。
这里面有一点需要注意的就是`RTSPServer`会同多个`RTSPClient`进行沟通，而`RTSPServer`为了同多个`Client`进行
沟通管理，就需要借助于`ServerMediaSession`来进行。这就是为什么我们会在`RTSPServer`当中看到了一个`ServerMediaSession`
队列，就是用于管理多个`RTSPClient`.

之所以`RTSPServer`会要同多个`RTSPClient`进行交互，主要还是因为`RTSP`协议有一个很重要的使用场合，即`视频会议`系统，
对于`视频会议`系统，通常是基于组播来时间，这样就会有多个客户端.

所以才会有`ServerMediaSession`来专门用于管理各个回话的状态。
`RTSPServer`本身则只是负责处理`rtsp`协议的请求。







