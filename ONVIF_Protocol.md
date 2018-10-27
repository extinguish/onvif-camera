## 关于`ONVIF`协议的请求包和返回包的格式

### GetStreamUri

由客户端发送的`GetStreamUri`请求包:

```xml
<GetStreamUri xmlns="http://www.onvif.org/ver10/media/wsdl">
<StreamSetup>
    <Stream xmlns="http://www.onvif.org/ver10/schema">RTP-Unicast</Stream>
    <Transport xmlns="http://www.onvif.org/ver10/schema">
        <Protocol>UDP</Protocol>
    </Transport>
</StreamSetup>
<ProfileToken>profile-0_0</ProfileToken>
</GetStreamUri>
```

服务端在和收到上面的请求包之后，会给出类似下面这样的`packet`:
```xml
<trt:GetStreamUriResponse>
<trt:MediaUri>
    <tt:Uri>rtsp://192.168.0.105/live1.sdp</tt:Uri>
    <tt:InvalidAfterConnect>false</tt:InvalidAfterConnect>
    <tt:InvalidAfterReboot>false</tt:InvalidAfterReboot>
    <tt:Timeout>P1Y</tt:Timeout>
</trt:MediaUri>
</trt:GetStreamUriResponse>
```


---------------------------------------------------------

我们通常看到的教程大部分都是讲解如何同实现了ONVIF协议的IPCamera进行交互。但是很少有完整的讲解
如果实现一个IPCamera.





