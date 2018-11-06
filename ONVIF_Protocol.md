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

当我们测试`StreamUri`的请求和返回时，是结合[ONVIFCameraAndroid](https://github.com/rvi/ONVIFCameraAndroid.git)程序
来进行测试的.

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

以下是通过`ONVIF Device Test Tool`当中的手动触发`Probe`操作后，发送给我们的`Probe Packet`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<Envelope xmlns:tds="http://www.onvif.org/ver10/device/wsdl" xmlns="http://www.w3.org/2003/05/soap-envelope">
    <Header>
        <wsa:MessageID xmlns:wsa="http://schemas.xmlsoap.org/ws/2004/08/addressing">
            uuid:69f483ed-5bc7-4e3f-8a8c-7bfc63168aa0
        </wsa:MessageID>
        <wsa:To xmlns:wsa="http://schemas.xmlsoap.org/ws/2004/08/addressing">
            urn:schemas-xmlsoap-org:ws:2005:04:discovery
        </wsa:To>
        <wsa:Action xmlns:wsa="http://schemas.xmlsoap.org/ws/2004/08/addressing">
            http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe
        </wsa:Action>
    </Header>
    <Body>
        <Probe xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://schemas.xmlsoap.org/ws/2005/04/discovery">
            <Types>tds:Device</Types>
            <Scopes />
        </Probe>
    </Body>
</Envelope>
```

当我们使用`ONVIF Device Test Tool`当中的自动发现功能(`Discover Devices`)来寻找我们的设备时，我们通过
`WireShark`抓到的数据包是:
```xml
<?xml version="1.0" encoding="utf-8"?>
<Envelope xmlns:dn="http://www.onvif.org/ver10/network/wsdl"
    xmlns="http://www.w3.org/2003/05/soap-envelope">
    <Header>
        <wsa:MessageID xmlns:wsa="http://schemas.xmlsoap.org/ws/2004/08/addressing">
            uuid:81b611df-3d93-4422-a3f1-60c2a0064de3
        </wsa:MessageID>
        <wsa:To xmlns:wsa="http://schemas.xmlsoap.org/ws/2004/08/addressing">
            urn:schemas-xmlsoap-org:ws:2005:04:discovery
        </wsa:To>
        <wsa:Action xmlns:wsa="http://schemas.xmlsoap.org/ws/2004/08/addressing">
            http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe
        </wsa:Action>
    </Header>
    <Body>
        <Probe xmlns:xsd="http://www.w3.org/2001/XMLSchema"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xmlns="http://schemas.xmlsoap.org/ws/2005/04/discovery">
            <Types>dn:NetworkVideoTransmitter</Types>
            <Scopes />
        </Probe>
    </Body>
</Envelope>
```
他们的区别只是在于`Probe`的内容不一样.

但是对于不同的工具发送的`Probe Packet`的具体内容有些差异，我们需要对这些差异进行兼容.



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

按照`ONVIF Device Test Tool`的格式要求发送的返回`Probe-Match`数据包的具体样式:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<SOAP-ENV:Envelope xmlns:SOAP-ENC="http://www.w3.org/2003/05/soap-encoding" xmlns:SOAP-ENV="http://www.w3.org/2003/05/soap-envelope" xmlns:c14n="http://www.w3.org/2001/10/xml-exc-c14n#" xmlns:chan="http://schemas.microsoft.com/ws/2005/02/duplex" xmlns:ds="http://www.w3.org/2000/09/xmldsig#" xmlns:saml1="urn:oasis:names:tc:SAML:1.0:assertion" xmlns:saml2="urn:oasis:names:tc:SAML:2.0:assertion" xmlns:sizeds="http://tempuri.org/sizeds.xsd" xmlns:sizexenc="http://tempuri.org/sizexenc.xsd" xmlns:tdn="http://www.onvif.org/ver10/network/wsdl" xmlns:tds="http://www.onvif.org/ver10/device/wsdl" xmlns:tptz="http://www.onvif.org/ver20/ptz/wsdl" xmlns:trt="http://www.onvif.org/ver10/media/wsdl" xmlns:tt="http://www.onvif.org/ver10/schema" xmlns:wsa5="http://schemas.xmlsoap.org/ws/2004/08/addressing" xmlns:wsc="http://docs.oasis-open.org/ws-sx/ws-secureconversation/200512" xmlns:wsdd="http://schemas.xmlsoap.org/ws/2005/04/discovery" xmlns:wsnt="http://docs.oasis-open.org/wsn/b-2" xmlns:wsrfbf="http://docs.oasis-open.org/wsrf/bf-2" xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd" xmlns:wstop="http://docs.oasis-open.org/wsn/t-1" xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd" xmlns:xenc="http://www.w3.org/2001/04/xmlenc#" xmlns:xmime="http://tempuri.org/xmime.xsd" xmlns:xop="http://www.w3.org/2004/08/xop/include" xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <SOAP-ENV:Header>
        <wsa5:MessageID>urn:uuid:119d6358-a7a0-41e4-b167-f00199b0625d</wsa5:MessageID>
        <wsa5:RelatesTo>uuid:c6023c78-864b-4a48-a901-bebcf1088730</wsa5:RelatesTo>
        <wsa5:To SOAP-ENV:mustUnderstand="true">http://www.w3.org/2005/08/addressing/anonymous
        </wsa5:To>
        <wsa5:Action SOAP-ENV:mustUnderstand="true">
            http://schemas.xmlsoap.org/ws/2005/04/discovery/ProbeMatches
        </wsa5:Action>
        <wsdd:AppSequence InstanceId="0" MessageNumber="6"></wsdd:AppSequence>
    </SOAP-ENV:Header>
    <SOAP-ENV:Body>
        <wsdd:ProbeMatches>
            <wsdd:ProbeMatch>
                <wsa5:EndpointReference>
                    <wsa5:Address>http://10.0.0.50:1881/</wsa5:Address>
                </wsa5:EndpointReference>
                <wsdd:Types>tdn:NetworkVideoTransmitter tds:Device</wsdd:Types>
                <wsdd:Scopes>onvif://www.onvif.org/name/Company onvif://www.onvif.org/hardware/Imx
                    onvif://www.onvif.org/Profile/Streaming onvif://www.onvif.org/location/Any
                    onvif://www.onvif.org/type/NetworkVideoTransmitter
                    onvif://www.onvif.org/type/video_encoder
                    onvif://www.onvif.org/type/audio_encoder onvif://www.onvif.org/type/ptz
                </wsdd:Scopes>
                <wsdd:XAddrs>http://10.0.0.50:1881/</wsdd:XAddrs>
                <wsdd:MetadataVersion>1</wsdd:MetadataVersion>
            </wsdd:ProbeMatch>
        </wsdd:ProbeMatches>
    </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
```

不同的工具的封装成的数据格式是不一样的.

我们需要按照上面要求的格式进行封装我们的`ProbeMtach`数据包.

---------------------------------------------------------

我们通常看到的教程大部分都是讲解如何同实现了ONVIF协议的IPCamera进行交互。但是很少有完整的讲解
如果实现一个IPCamera.


----------------------------------------------------------

## 关于获取`RTSP Stream URI`的通信过程

以下讲述的过程，就是一个完整的通信流程(通信流程是通过对**Ocular**程序的运行过程转包来分析的,`Ocular`程序安装的
设备的`IP`地址是`172.16.0.50`).

### 1. onvif/device_service

**Request:**

```xml
<?xml version="1.0" encoding="utf-8"?>
<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope"
    xmlns:xsd="http://www.w3.org/2001/XMLSchema"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <soap:Body>
        <GetServices xmlns="http://www.onvif.org/ver10/device/wsdl">
            <IncludeCapability>false</IncludeCapability>
        </GetServices>
    </soap:Body>
</soap:Envelope>
```

如果这个请求的结果是:
> 下面的结果是`OkHttp`返回给我们的内容(主要是用于表示请求的过程是否顺利成功，对于onvif通信返回的内容，并不在下面的内容当中包含)

```
Response{protocol=http/1.1, code=200, message=OK, url=http://172.16.0.50:8081/onvif/device_service}
```

代表请求成功，那么这次请求对应的`onvif`数据为如下(这才是我们真正关心的内容):

```xml
<?xml version='1.0' encoding='utf-8' ?>
<s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope"
    xmlns:tds="http://www.onvif.org/ver10/device/wsdl"
    xmlns:tev="http://www.onvif.org/ver10/events/wsdl"
    xmlns:timg="http://www.onvif.org/ver20/imaging/wsdl"
    xmlns:tptz="http://www.onvif.org/ver20/ptz/wsdl"
    xmlns:trt="http://www.onvif.org/ver10/media/wsdl"
    xmlns:tt="http://www.onvif.org/ver10/schema">
    <s:Body>
        <tds:GetServicesResponse>
            <tds:Service>
                <tds:Namespace>http://www.onvif.org/ver10/device/wsdl</tds:Namespace>
                <tds:XAddr>http://172.16.0.50:8081:8081/onvif/device_service</tds:XAddr>
                <tds:Capabilities>
                    <tds:Capabilities>
                        <tds:Network
                            DHCPv6="false"
                            Dot11Configuration="false"
                            Dot1XConfigurations="0"
                            DynDNS="false"
                            HostnameFromDHCP="true"
                            IPFilter="true"
                            IPVersion6="false"
                            NTP="1"
                            ZeroConfiguration="true" />
                        <tds:Security
                            AccessPolicyConfig="true"
                            DefaultAccessPolicy="false"
                            Dot1X="false"
                            HttpDigest="false"
                            KerberosToken="false"
                            MaxUsers="10"
                            OnboardKeyGeneration="false"
                            RELToken="false"
                            RemoteUserHandling="false"
                            SAMLToken="false"
                            TLS1.0="false"
                            TLS1.1="false"
                            TLS1.2="false"
                            UsernameToken="true"
                            X.509Token="false" />
                        <tds:System
                            DiscoveryBye="true"
                            DiscoveryResolve="true"
                            FirmwareUpgrade="false"
                            HttpFirmwareUpgrade="false"
                            HttpSupportInformation="false"
                            HttpSystemBackup="false"
                            HttpSystemLogging="false"
                            RemoteDiscovery="false"
                            SystemBackup="false"
                            SystemLogging="false" />
                    </tds:Capabilities>
                </tds:Capabilities>
                <tds:Version>
                    <tt:Major>1</tt:Major>
                    <tt:Minor>70</tt:Minor>
                </tds:Version>
            </tds:Service>
            <tds:Service>
                <tds:Namespace>http://www.onvif.org/ver10/events/wsdl</tds:Namespace>
                <tds:XAddr>http://172.16.0.50:8081:8081/event/evtservice</tds:XAddr>
                <tds:Capabilities>
                    <tev:Capabilities
                        MaxNotificationProducers="6"
                        MaxPullPoints="2"
                        PersistentNotificationStorage="false"
                        WSPausableSubscriptionManagerInterfaceSupport="false"
                        WSPullPointSupport="false"
                        WSSubscriptionPolicySupport="false" />
                </tds:Capabilities>
                <tds:Version>
                    <tt:Major>1</tt:Major>
                    <tt:Minor>70</tt:Minor>
                </tds:Version>
            </tds:Service>
            <tds:Service>
                <tds:Namespace>http://www.onvif.org/ver20/imaging/wsdl</tds:Namespace>
                <tds:XAddr>http://172.16.0.50:8081:8081/onvif/imaging</tds:XAddr>
                <tds:Capabilities>
                    <timg:Capabilities ImageStabilization="false" />
                </tds:Capabilities>
                <tds:Version>
                    <tt:Major>2</tt:Major>
                    <tt:Minor>30</tt:Minor>
                </tds:Version>
            </tds:Service>
            <tds:Service>
                <tds:Namespace>http://www.onvif.org/ver10/media/wsdl</tds:Namespace>
                <tds:XAddr>http://172.16.0.50:8081:8081/onvif/media</tds:XAddr>
                <tds:Capabilities>
                    <trt:Capabilities
                        OSD="false"
                        Rotation="false"
                        SnapshotUri="true"
                        VideoSourceMode="false">
                        <trt:ProfileCapabilities MaximumNumberOfProfiles="10" />
                        <trt:StreamingCapabilities
                            NoRTSPStreaming="false"
                            NonAggregateControl="false"
                            RTPMulticast="false"
                            RTP_RTSP_TCP="true"
                            RTP_TCP="false" />
                    </trt:Capabilities>
                </tds:Capabilities>
                <tds:Version>
                    <tt:Major>1</tt:Major>
                    <tt:Minor>70</tt:Minor>
                </tds:Version>
            </tds:Service>
            <tds:Service>
                <tds:Namespace>http://www.onvif.org/ver20/ptz/wsdl</tds:Namespace>
                <tds:XAddr>http://172.16.0.50:8081:8081/onvif/ptz</tds:XAddr>
                <tds:Capabilities>
                    <tptz:Capabilities
                        EFlip="false"
                        GetCompatibleConfigurations="false"
                        Reverse="false" />
                </tds:Capabilities>
                <tds:Version>
                    <tt:Major>2</tt:Major>
                    <tt:Minor>50</tt:Minor>
                </tds:Version>
            </tds:Service>
        </tds:GetServicesResponse>
    </s:Body>
</s:Envelope>

```

然后就是获取设备信息:(获取设备详细信息)

```xml
<?xml version="1.0" encoding="utf-8"?>
<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope"
    xmlns:xsd="http://www.w3.org/2001/XMLSchema"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <soap:Body>
        <GetDeviceInformation xmlns="http://www.onvif.org/ver10/device/wsdl"></GetDeviceInformation>
    </soap:Body>
</soap:Envelope>
```

然后是请求`Profile`信息:(`Profile`信息当中主要包含的是设备的一些硬件信息，例如镜头的分辨率，以及要传输的视频的比特率，帧率等信息)
包体是:
```xml
<?xml version="1.0" encoding="utf-8"?>
<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope"
    xmlns:xsd="http://www.w3.org/2001/XMLSchema"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <soap:Body>
        <GetProfiles xmlns="http://www.onvif.org/ver10/media/wsdl" />
    </soap:Body>
</soap:Envelope>
```

如果该接口请求顺利的话，即`HTTP`的执行的结果为如下所示:
```
Response{protocol=http/1.1, code=200, message=OK, url=http://172.16.0.50:8081/onvif/device_service}
```
的话，那么就可以得到`GetProfiles`的请求结果：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<env:Envelope xmlns:env="http://www.w3.org/2003/05/soap-envelope"
    xmlns:trt="http://www.onvif.org/ver10/media/wsdl"
    xmlns:tt="http://www.onvif.org/ver10/schema">
    <env:Body>
        <trt:GetProfilesResponse>
            <trt:Profiles
                fixed="false"
                token="Profile1">
                <tt:Name>Profile1</tt:Name>
                <tt:VideoSourceConfiguration token="VideoSourceConfiguration0_0">
                    <tt:Name>VideoSourceConfiguration0_0</tt:Name>
                    <tt:UseCount>1</tt:UseCount>
                    <tt:SourceToken>VideoSource0</tt:SourceToken>
                    <tt:Bounds
                        height="1080"
                        width="1920"
                        x="0"
                        y="0" />
                </tt:VideoSourceConfiguration>
                <tt:VideoEncoderConfiguration token="VideoEncoderConfiguration0_0">
                    <tt:Name>VideoEncoderConfiguration0_0</tt:Name>
                    <tt:UseCount>3683892</tt:UseCount>
                    <tt:Encoding>H264</tt:Encoding>
                    <tt:Resolution>
                        <tt:Width>1920</tt:Width>
                        <tt:Height>1080</tt:Height>
                    </tt:Resolution>
                    <tt:Quality>44.0</tt:Quality>
                    <tt:RateControl>
                        <tt:FrameRateLimit>5</tt:FrameRateLimit>
                        <tt:EncodingInterval>1</tt:EncodingInterval>
                        <tt:BitrateLimit>2000</tt:BitrateLimit>
                    </tt:RateControl>
                    <tt:Multicast>
                        <tt:Address>
                            <tt:Type>IPv4</tt:Type>
                            <tt:IPv4Address>0.0.0.0</tt:IPv4Address>
                            <tt:IPv6Address />
                        </tt:Address>
                        <tt:Port>0</tt:Port>
                        <tt:TTL>0</tt:TTL>
                        <tt:AutoStart>false</tt:AutoStart>
                    </tt:Multicast>
                    <tt:SessionTimeout>PT30S</tt:SessionTimeout>
                </tt:VideoEncoderConfiguration>
            </trt:Profiles>
        </trt:GetProfilesResponse>
    </env:Body>
</env:Envelope>
```

最后就是开始准备请求`RTSP`的视频地址:

以下是请求体:

```xml
<?xml version="1.0" encoding="utf-8"?>
<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope"
    xmlns:xsd="http://www.w3.org/2001/XMLSchema"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <soap:Body>
        <GetStreamUri xmlns="http://www.onvif.org/ver20/media/wsdl">
            <ProfileToken>Profile1</ProfileToken>
            <Protocol>RTSP</Protocol>
        </GetStreamUri>
    </soap:Body>
</soap:Envelope>
```

如果`HTTP`请求返回的内容是:

```
Response{protocol=http/1.1, code=200, message=OK, url=http://172.16.0.50:8081/onvif/device_service}
```

代表该请求在`HTTP`协议层面执行成功，然后就可以获取到正式的`ONVIF`协议返回内容:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<env:Envelope xmlns:env="http://www.w3.org/2003/05/soap-envelope"
    xmlns:trt="http://www.onvif.org/ver10/media/wsdl"
    xmlns:tt="http://www.onvif.org/ver10/schema">
    <env:Body>
        <trt:GetStreamUriResponse>
            <trt:MediaUri>
                <tt:Uri>rtsp://172.16.0.50:8081:8081/h264</tt:Uri>
                <tt:InvalidAfterConnect>false</tt:InvalidAfterConnect>
                <tt:InvalidAfterReboot>false</tt:InvalidAfterReboot>
                <tt:Timeout>P1Y</tt:Timeout>
            </trt:MediaUri>
        </trt:GetStreamUriResponse>
    </env:Body>
</env:Envelope>
```

### 2. 


