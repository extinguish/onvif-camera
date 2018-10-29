## 关于`ONVIF`协议的请求包和返回包的格式

### GetStreamUri

由客户端(即`IPCamera-Viewer`)发送的`GetStreamUri`请求包:

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

服务端(即`IPCamera`)在接收到上面的请求包之后，会给出类似下面这样的`packet`:
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

只要我们将`rtsp`的地址交给`IPCamera-Viewer`，客户端就可以观看视频了(后期的视频控制，通过另外的`ws-service`实现).

--------------------------------------------------------

### `Probe` Packet

以下是由`IPCamera-Viewer`发送的用于探测当中局域网内的`IPCamera`的数据报示例:

```xml
<s:Envelope
    xmlns:a="http://www.w3.org/2005/08/addressing"
    xmlns:d="http://docs.oasis-open.org/ws-dd/ns/discovery/2009/01"
    xmlns:i="http://printer.example.org/2003/imaging"
    xmlns:s="http://www.w3.org/2003/05/soap-envelope">
    <s:Header>
        <a:Action>http://docs.oasis-open.org/ws-dd/ns/discovery/2009/01/Probe</a:Action>
        <a:MessageID>
            urn:uuid:0a6dc791-2be6-4991-9af1-454778a1917a
        </a:MessageID>
        <a:To>urn:docs-oasis-open-org:ws-dd:ns:discovery:2009:01</a:To>
    </s:Header>
    <s:Body>
        <d:Probe>
            <d:Types>i:PrintBasic</d:Types>
            <d:Scopes
                MatchBy="http://docs.oasis-open.org/ws-dd/ns/discovery/2009/01/ldap">
                ldap:///ou=engineering,o=examplecom,c=us
            </d:Scopes>
        </d:Probe>
    </s:Body>
</s:Envelope>
```

----------------------------------------------------------------

### `Probe-Match` Packet
当`IPCamera`在收到由`IPCamera-Viewer`发送的`Discovery`包之后，会给`IPCamera-Viewer`一个响应。
以下就是这种响应包的一个具体示例:(取自[ws-discovery协议官网讲解](http://docs.oasis-open.org/ws-dd/discovery/1.1/os/wsdd-discovery-1.1-spec-os.html))

```xml
<s:Envelope xmlns:a="http://www.w3.org/2005/08/addressing"
    xmlns:d="http://docs.oasis-open.org/ws-dd/ns/discovery/2009/01"
    xmlns:i="http://printer.example.org/2003/imaging"
    xmlns:s="http://www.w3.org/2003/05/soap-envelope">
    <s:Header>
        <a:Action>http://docs.oasis-open.org/ws-dd/ns/discovery/2009/01/ProbeMatches</a:Action>
        <a:MessageID>urn:uuid:e32e6863-ea5e-4ee4-997e-69539d1ff2cc</a:MessageID>
        <a:RelatesTo>urn:uuid:0a6dc791-2be6-4991-9af1-454778a1917a
        </a:RelatesTo>
        <a:To>http://www.w3.org/2005/08/addressing/anonymous
        </a:To>
        <d:AppSequence
            InstanceId="1077004800"
            MessageNumber="2" />
    </s:Header>
    <s:Body>
        <d:ProbeMatches>
            <d:ProbeMatch>
                <a:EndpointReference>
                    <a:Address>urn:uuid:98190dc2-0890-4ef8-ac9a-5940995e6119</a:Address>
                </a:EndpointReference>
                <d:Types>i:PrintBasic i:PrintAdvanced</d:Types>
                <d:Scopes>
                    ldap:///ou=engineering,o=examplecom,c=us
                    ldap:///ou=floor1,ou=b42,ou=anytown,o=examplecom,c=us
                    http://itdept/imaging/deployment/2004-12-04
                </d:Scopes>
                <d:XAddrs>http://prn-example/PRN42/b42-1668-a</d:XAddrs>
                <d:MetadataVersion>75965</d:MetadataVersion>
            </d:ProbeMatch>
        </d:ProbeMatches>
    </s:Body>
</s:Envelope>
```

我们需要按照上面要求的格式进行封装我们的`ProbeMtach`数据包.

---------------------------------------------------------

我们通常看到的教程大部分都是讲解如何同实现了ONVIF协议的IPCamera进行交互。但是很少有完整的讲解
如果实现一个IPCamera.





