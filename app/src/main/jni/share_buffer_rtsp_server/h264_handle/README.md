## 关于`h264_handle`的简要说明

`h264_handle`是从`RtspService`当中直接移植过来的，修改的部分主要是最终的数据发送部分，`RtmpService`
主要是将编码好的数据通过`rtmp`协议发送出去，而我们目前这里主要是将编码好的数据通过`rtsp`协议发送出去.

--------------------------------------------------------

在我们最初的设想当中，我们计划是让`h264_handle`模块依赖`adas_ipcamera`模块。
即`h264_handle`作为**主控制方**来操作整体流程。
但是后来我们发现不可行，因为`AdasIPCamera`本身是一个`Rtsp Server`,即是作为一个
完整的服务器存在的，因此需要他去响应外界的请求，而不是完全的由我们自己来控制，作为对比
就是我们之前实现的`librtmp`,`librtmp`只是作为一个推流端，严格意义上来说并不是一个`server`,
即他是不需要响应来自远程的`client`的请求，而只是需要将数据推送到指定的`server`上就可以了。
因此对于`librtmp`我们直接从`Java`层就可以来控制整个逻辑，即自己内部就完全可以控制了.

因此我们同时还需要`adas_ipcamera`来控制`h264_handle`模块，
如果`adas_ipcamera`和`h264_handle`分别作为两个模块编译的话，就会造成循环依赖，即互相依赖。
因此我们需要将他们两个合并成一个模块.

--------------------------------------------------------

但其实从更严格的角度来说，应该是`adas_ipcamera`依赖于`h264_handle`模块，因为只有`adas_ipcamera`才
是唯一的控制方.

例如我们可以在实现当中，将要填充的队列传递给`h264_handle`,然后再控制`h264_handle`的启动，开始和暂停操作.


--------------------------------------------------------

在我们最新的实现当中,`开始编码操作`的初始化是直接在`Java`层进行，然后具体从`编好码`的`帧队列`当中读取数据
则是有`openRTSP`这种`rtsp client`来决定,即只有`rtsp client`真的开始请求数据了，我们才真的开始从`encoded_raw_frame_queue`
当中读取视频帧数据，然后进行上传.

在这里，`encoded_raw_frame_queue`就扮演了类似于`v4l2rtspserver`当中`视频设备文件`的角色,即文件一直都在，
就看`rtspserver`什么时候去读取了。
同样的对于`AdasIPCameraRtspServer`来说，`encoded_raw_frame_queue`也是一直都存在，主要看`AdasIPCameraViewer`
什么时候向`AdasIPCameraRtspServer`去请求视频流.
只要`AdasIPCameraViewer`开始请求视频流，那么就开始消费`encoded_raw_frame_queue`当中的数据.







