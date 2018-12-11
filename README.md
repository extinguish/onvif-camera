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


------------------------------------------------------------------

## 2018-12-03

当前分支是`live555_rtsp_server`

我们目前主要由两种实现方式:
第一种是通知KyEncoder开始向本地当中写入编好码的数据
然后会产生一个文件描述符，然后就是通知DeviceSource来读取该文件当中的内容
第二种就是直接feed frame给DeviceSource,但是还不确定这种方式是否会有其他的问题.

但是对于以上提到的两种方式，onvif_ipcam_share_buffer分支当中主要在尝试第一种方式.

我们当前分支`live555_rtsp_server`则主要是用于通过`callback`的形式来将编码好的数据直接传递到`rtsp-server`当中，而不使用文件的形式来实现.
因为我们暂时不知道如何将`share-buffer`编码好的数据当做一个`设备文件`(类似于`v4l2`那样的设备)，如果我们不知道这样
如何做的话，那么就会出现一个问题，就是我们创建的实体文件如何管理，如果文件太大的话，如何处理，还有时序问题.

其实从另一个角度进行考虑，`rtsp`只是之前我们实现的`librtmp`的一个替代，从这个角度来进行考虑的话，问题就会简化很多。
之所以目前我们很难实现`rtsp`和`h264_encoder`之间的联动，主要还是考虑问题的角度出现了问题，我们当前一直认为，应该是
`rtsp`依赖`h264_encoder`，然后`rtsp`是主动方，
但是其实不是这样的，`h264_encoder`才是主动方，因为`h264_encoder`是数据提供方，`rtsp`只是一个普通的消费者，同`rtmp`
是一样的.

当然这里面还稍微有一些细节的区别,就是在实现`rtmp`时，我们只是作为一个推流端，接受的控制指令全部都是在我们本地完成的，
而对于`rtsp`来说，接受的指令已经是来自于另一台远程设备了，这一台远程设备是作为一个客户端来向我们发送请求的，所以严格意义
上来说，`rtsp`才是控制端，`h264_handle`就不是控制端了.

不同的角度对应的是不同的处理方式.

所以相对来说，`文件fd`才是最好的处理方式。

或者说一种理想的方式，就是`rtsp`是绝对的控制方，然后内部通过`FramedSource`来控制`编码`操作.

----------------------------------------------------------------------------------------------------

其实我们从一开始移植`rtmp`就出问题了.

对于`librtmp`来说，我们自己就是主动方，即我们自己就可以进行控制.但是对于目前的实现来说，client才是主动方.

----------------------------------------------------------------------------------------------------

About `pure_onvif_implementation` branch

This branch has nothing to do with rtsp server, but just focused on `ONVIF` protocol implementation.







