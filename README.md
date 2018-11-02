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
