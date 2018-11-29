## 关于`ONVIF-SpyDroid-Camera`

这里是将`Android`设备实现成`ONVIF`协议的`server`.

可以通过[ONVIF-Camera](https://github.com/yashrs/ONVIF-Camera.git)工程来验证`ONVIF-SpyDroid-Camera`
是否工作正常。
如果通过[ONVIF-Camera](https://github.com/yashrs/ONVIF-Camera.git)可以连接到`ONVIF-SpyDroid-Camera`
的话，就代表`Discovery`协议是可以工作。

> 目前[ONVIF-Camera](https://github.com/yashrs/ONVIF-Camera.git)有一些问题，就是拉取到`RTSP`流之后播放有问题<br>，只可以播放一帧，应该是视频格式不支持。但是通过`ffplay`是可以播放`ONVIF-SpyDroid-Camera`的`RTSP`视频流的.

--------------------------------------------------------------

## 目前存在的问题

当前分支是`onvif_ipcam_share_buffer`.在`onvif_ipcam_share_buffer`分支当中,我们是从`Share Buffer`当中直接读取
视频流数据,而不是借助于`Camera` api来读取视频数据.

另外我们从`Share Buffer`读取数据时,是没有音频数据的.
我们也不处理音频数据.


> `SpyDroid-IpCamera`本身的视频可以通过`ffplay`进行播放,例如对于`ffplay rtsp://172.16.0.35:8086`可以完全正常的播放视频流.但是`VLC`在很多情况下无法播放我们的视频流.<br>因此我们需要通过`fflpay`验证我们的视频推流是否正常,而不是通过`VLC`验证.

----------------------------------------------------------------

## 2018-11-29

> 我们修改了`RTSP`的实现从`libstreaming`切换到`live555`来实现.

`Java`层的实现限制太多了,`Java`层的实现主要就是借助于`libstreaming`来实现的，而且目前`github`上所有的借助于`Java`实现的`rtsp server`
基本上都是借鉴或者引用`libstreaming`来实现的;但是`libstreaming`本身的实现当中基本上就是认为`rtsp`的当中的视频流的传输就是应该通过
`rtp/avp/udp`来进行传输(这当然没有错，因为在视频流领域确实是应该按照udp来实现才能达到最好的效果,关键基于udp的rtp要比基于tcp的udp
要复杂的很多,毕竟基于udp的rtp需要同时维持两个端口，即发送端和接收端两个端口)，所以可看到代码结构当中几乎就是按照udp来进行组织的，完
全不打算考虑tcp的实现了;如果我们要使用tcp来实现的话，需要修改的东西很多,短期内无法很快完成.
