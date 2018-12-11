package com.adasplus.onvif.http;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.adasplus.onvif.Utilities;

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.RequestLine;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ContentProducer;
import org.apache.http.entity.EntityTemplate;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URLDecoder;
import java.util.Locale;

/**
 * 同{@link ModAssetServer}的工作方式一样。
 * 只是这里针对的是ONVIF协议的处理.
 */
public class ModOnvifServer implements HttpRequestHandler {
    private static final String TAG = "ModOnvifServer";

    /**
     * 参考{@link ModAssetServer#PATTERN}.
     */
    public static final String PATTERN = "/onvif/*";

    /**
     * ONVIF协议支持的数据格式
     */
    public static final String[] MIME_MEDIA_TYPES = {
            "xml", "onvif/xml",
            "wsdl", "onvif/wsdl",
            "xsd", "onvif/xsd"
    };

    private final TinyHttpServer mServer;

    private final Context mContext;

    private int mRtspServerPort;

    private static final int DEFAULT_RTSP_PORT = 8086;
    private static final String DEFAULT_RTSP_ADDRESS = "192.168.100.110";

    public ModOnvifServer(TinyHttpServer server) {
        this.mServer = server;
        mContext = server.getContext();
        mRtspServerPort = DEFAULT_RTSP_PORT;
    }

    /**
     * @param httpRequest
     * @param httpResponse
     * @param httpContext
     * @throws HttpException
     * @throws IOException
     */
    @Override
    public void handle(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) throws HttpException, IOException {
        AbstractHttpEntity body;

        Log.d(TAG, "Handle the request in ModOnvifServer");
        RequestLine requestLine = httpRequest.getRequestLine();
        String method = requestLine.getMethod().toUpperCase(Locale.ENGLISH);
        Log.i(TAG, "requestMethod are " + method);
        if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("POST")) {
            Log.e(TAG, "the request method \"" + method + "\" are not GET HEAD or POST, we cannot process such request");
            throw new MethodNotSupportedException(method + " method not supported");
        }

        final String url = URLDecoder.decode(httpRequest.getRequestLine().getUri());
        Log.d(TAG, "the request URL are " + url);
        if (httpRequest instanceof HttpEntityEnclosingRequest) {
            HttpEntity entity = ((HttpEntityEnclosingRequest) httpRequest).getEntity();
            byte[] entityContent = EntityUtils.toByteArray(entity);

            String requestContent = new String(entityContent);
            Log.d(TAG, "the request content are " + requestContent);
            // 关于不同的请求内容对应的不同的含义，直接参考位于项目根目录当中的ONVIF_Protocol.md
            // 以下的请求结果返回都是/onvif/device_service接口的返回数据
            httpResponse.setStatusCode(HttpStatus.SC_OK);
            if (requestContent.contains("GetServices")) {
                Log.d(TAG, "is GetServices interface");
                Log.d(TAG, "return the device base info");
                String currentDevIpAddress = Utilities.getLocalDevIp(mContext);
                String getServicesResponse = constructOnvifDeviceServiceResponse(currentDevIpAddress);
                byte[] responseContentByteArr = getServicesResponse.getBytes("UTF-8");
                InputStream servicesResponseInputStream = new ByteArrayInputStream(responseContentByteArr);
                body = new InputStreamEntity(servicesResponseInputStream, responseContentByteArr.length);
            } else if (requestContent.contains("GetDeviceInformation")) {
                Log.d(TAG, "is GetDeviceInformation interface");
                String getDevInfoResponse = constructOnvifDevInfoResponse(DEV_MANUFACTURE, DEV_MODEL,
                        DEV_FIRMWARE_VERSION, DEV_SERIAL_NUM, Utilities.getDevId(mContext));
                byte[] responseContentByteArr = getDevInfoResponse.getBytes("UTF-8");
                InputStream devInfoResponseInputStream = new ByteArrayInputStream(responseContentByteArr);
                body = new InputStreamEntity(devInfoResponseInputStream, responseContentByteArr.length);
            } else if (requestContent.contains("GetProfiles")) {
                Log.d(TAG, "is GetProfiles interface");
                // TODO: the following video attributes value should be provided by the native IPCamera
                final int videoResWidth = 320;
                final int videoResHeight = 240;
                final int videoBitRate = 135000;
                String getProfilesResponse = constructOnvifGetProfilesResponse(videoResWidth, videoResHeight, videoBitRate);
                byte[] responseContentByteArr = getProfilesResponse.getBytes("UTF-8");
                InputStream profileResponseInputStream = new ByteArrayInputStream(responseContentByteArr);
                body = new InputStreamEntity(profileResponseInputStream, responseContentByteArr.length);
            } else if (requestContent.contains("GetStreamUri")) {
                Log.d(TAG, "is GetStreamUri interface");
                String rtspServerUrl = "rtsp://" + Utilities.getLocalDevIp(mContext) + ":" + mRtspServerPort + "/";
                String getStreamUriResponse = constructOnvifStreamUriResponse(rtspServerUrl);
                byte[] responseContentByteArr = getStreamUriResponse.getBytes("UTF-8");
                InputStream streamUriContentInputStream = new ByteArrayInputStream(responseContentByteArr);
                body = new InputStreamEntity(streamUriContentInputStream, responseContentByteArr.length);
            } else {
                Log.e(TAG, "not known interface");
                httpResponse.setStatusCode(HttpStatus.SC_NOT_FOUND);
                body = new EntityTemplate(new ContentProducer() {
                    public void writeTo(final OutputStream outstream) throws IOException {
                        OutputStreamWriter writer = new OutputStreamWriter(outstream, "UTF-8");
                        writer.write("<html><body><h1>");
                        writer.write("File ");
                        writer.write("www" + url);
                        writer.write(" not found");
                        writer.write("</h1></body></html>");
                        writer.flush();
                    }
                });
                httpResponse.setEntity(body);
                return;
            }
            // body.setContentType("onvif/xml; charset=UTF-8");
            httpResponse.setEntity(body);
        }
    }

    private static final String DEV_MANUFACTURE = Build.MANUFACTURER;
    private static final String DEV_MODEL = Build.MODEL;
    private static final String DEV_FIRMWARE_VERSION = Build.VERSION.RELEASE;
    @SuppressLint("HardwareIds")
    private static final String DEV_SERIAL_NUM = Build.SERIAL;

    /**
     * 针对GetServices接口的返回数据
     * <p>
     * 返回数据是直接根据Ocular程序运行抓包获取到的(因为没有Ocular的源码，所以只能这样获取
     * 数据包)
     *
     * @param localIpAddress 当前设备的IP地址
     */
    private String constructOnvifDeviceServiceResponse(String localIpAddress) {
        String responseContent = "<?xml version='1.0' encoding='utf-8' ?>\n" +
                "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\"\n" +
                "    xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\"\n" +
                "    xmlns:tev=\"http://www.onvif.org/ver10/events/wsdl\"\n" +
                "    xmlns:timg=\"http://www.onvif.org/ver20/imaging/wsdl\"\n" +
                "    xmlns:tptz=\"http://www.onvif.org/ver20/ptz/wsdl\"\n" +
                "    xmlns:trt=\"http://www.onvif.org/ver10/media/wsdl\"\n" +
                "    xmlns:tt=\"http://www.onvif.org/ver10/schema\">\n" +
                "    <s:Body>\n" +
                "        <tds:GetServicesResponse>\n" +
                "            <tds:Service>\n" +
                "                <tds:Namespace>http://www.onvif.org/ver10/device/wsdl</tds:Namespace>\n" +
                "                <tds:XAddr>http://" + localIpAddress + ":8081:8081/onvif/device_service</tds:XAddr>\n" +
                "                <tds:Capabilities>\n" +
                "                    <tds:Capabilities>\n" +
                "                        <tds:Network\n" +
                "                            DHCPv6=\"false\"\n" +
                "                            Dot11Configuration=\"false\"\n" +
                "                            Dot1XConfigurations=\"0\"\n" +
                "                            DynDNS=\"false\"\n" +
                "                            HostnameFromDHCP=\"true\"\n" +
                "                            IPFilter=\"true\"\n" +
                "                            IPVersion6=\"false\"\n" +
                "                            NTP=\"1\"\n" +
                "                            ZeroConfiguration=\"true\" />\n" +
                "                        <tds:Security\n" +
                "                            AccessPolicyConfig=\"true\"\n" +
                "                            DefaultAccessPolicy=\"false\"\n" +
                "                            Dot1X=\"false\"\n" +
                "                            HttpDigest=\"false\"\n" +
                "                            KerberosToken=\"false\"\n" +
                "                            MaxUsers=\"10\"\n" +
                "                            OnboardKeyGeneration=\"false\"\n" +
                "                            RELToken=\"false\"\n" +
                "                            RemoteUserHandling=\"false\"\n" +
                "                            SAMLToken=\"false\"\n" +
                "                            TLS1.0=\"false\"\n" +
                "                            TLS1.1=\"false\"\n" +
                "                            TLS1.2=\"false\"\n" +
                "                            UsernameToken=\"true\"\n" +
                "                            X.509Token=\"false\" />\n" +
                "                        <tds:System\n" +
                "                            DiscoveryBye=\"true\"\n" +
                "                            DiscoveryResolve=\"true\"\n" +
                "                            FirmwareUpgrade=\"false\"\n" +
                "                            HttpFirmwareUpgrade=\"false\"\n" +
                "                            HttpSupportInformation=\"false\"\n" +
                "                            HttpSystemBackup=\"false\"\n" +
                "                            HttpSystemLogging=\"false\"\n" +
                "                            RemoteDiscovery=\"false\"\n" +
                "                            SystemBackup=\"false\"\n" +
                "                            SystemLogging=\"false\" />\n" +
                "                    </tds:Capabilities>\n" +
                "                </tds:Capabilities>\n" +
                "                <tds:Version>\n" +
                "                    <tt:Major>1</tt:Major>\n" +
                "                    <tt:Minor>70</tt:Minor>\n" +
                "                </tds:Version>\n" +
                "            </tds:Service>\n" +
                "            <tds:Service>\n" +
                "                <tds:Namespace>http://www.onvif.org/ver10/events/wsdl</tds:Namespace>\n" +
                "                <tds:XAddr>http://" + localIpAddress + ":8081:8081/event/evtservice</tds:XAddr>\n" +
                "                <tds:Capabilities>\n" +
                "                    <tev:Capabilities\n" +
                "                        MaxNotificationProducers=\"6\"\n" +
                "                        MaxPullPoints=\"2\"\n" +
                "                        PersistentNotificationStorage=\"false\"\n" +
                "                        WSPausableSubscriptionManagerInterfaceSupport=\"false\"\n" +
                "                        WSPullPointSupport=\"false\"\n" +
                "                        WSSubscriptionPolicySupport=\"false\" />\n" +
                "                </tds:Capabilities>\n" +
                "                <tds:Version>\n" +
                "                    <tt:Major>1</tt:Major>\n" +
                "                    <tt:Minor>70</tt:Minor>\n" +
                "                </tds:Version>\n" +
                "            </tds:Service>\n" +
                "            <tds:Service>\n" +
                "                <tds:Namespace>http://www.onvif.org/ver20/imaging/wsdl</tds:Namespace>\n" +
                "                <tds:XAddr>http://" + localIpAddress + ":8081/onvif/imaging</tds:XAddr>\n" +
                "                <tds:Capabilities>\n" +
                "                    <timg:Capabilities ImageStabilization=\"false\" />\n" +
                "                </tds:Capabilities>\n" +
                "                <tds:Version>\n" +
                "                    <tt:Major>2</tt:Major>\n" +
                "                    <tt:Minor>30</tt:Minor>\n" +
                "                </tds:Version>\n" +
                "            </tds:Service>\n" +
                "            <tds:Service>\n" +
                "                <tds:Namespace>http://www.onvif.org/ver10/media/wsdl</tds:Namespace>\n" +
                "                <tds:XAddr>http://" + localIpAddress + ":8081:8081/onvif/media</tds:XAddr>\n" +
                "                <tds:Capabilities>\n" +
                "                    <trt:Capabilities\n" +
                "                        OSD=\"false\"\n" +
                "                        Rotation=\"false\"\n" +
                "                        SnapshotUri=\"true\"\n" +
                "                        VideoSourceMode=\"false\">\n" +
                "                        <trt:ProfileCapabilities MaximumNumberOfProfiles=\"10\" />\n" +
                "                        <trt:StreamingCapabilities\n" +
                "                            NoRTSPStreaming=\"false\"\n" +
                "                            NonAggregateControl=\"false\"\n" +
                "                            RTPMulticast=\"false\"\n" +
                "                            RTP_RTSP_TCP=\"true\"\n" +
                "                            RTP_TCP=\"false\" />\n" +
                "                    </trt:Capabilities>\n" +
                "                </tds:Capabilities>\n" +
                "                <tds:Version>\n" +
                "                    <tt:Major>1</tt:Major>\n" +
                "                    <tt:Minor>70</tt:Minor>\n" +
                "                </tds:Version>\n" +
                "            </tds:Service>\n" +
                "            <tds:Service>\n" +
                "                <tds:Namespace>http://www.onvif.org/ver20/ptz/wsdl</tds:Namespace>\n" +
                "                <tds:XAddr>http://" + localIpAddress + ":8081:8081/onvif/ptz</tds:XAddr>\n" +
                "                <tds:Capabilities>\n" +
                "                    <tptz:Capabilities\n" +
                "                        EFlip=\"false\"\n" +
                "                        GetCompatibleConfigurations=\"false\"\n" +
                "                        Reverse=\"false\" />\n" +
                "                </tds:Capabilities>\n" +
                "                <tds:Version>\n" +
                "                    <tt:Major>2</tt:Major>\n" +
                "                    <tt:Minor>50</tt:Minor>\n" +
                "                </tds:Version>\n" +
                "            </tds:Service>\n" +
                "        </tds:GetServicesResponse>\n" +
                "    </s:Body>\n" +
                "</s:Envelope>\n";

        return responseContent;
    }


    /**
     * 用于响应IPCamera-Viewer发送的查看设备信息的请求
     *
     * @param manufacture     设备的生厂商,对于Ocular设备来说，这个值是: Emiliano Schmid
     * @param model           设备的型号名称,对于Ocular设备来说，这个值是: Ocular
     * @param firmwareVersion 设备的固件版本号,对于Ocular设备来说，这个值是: none
     * @param serialNum       设备的序列号,对于Ocular设备来说，这个值是:000001
     * @param hardwareID      设备的硬件ID(例如IMEI号码),对于Ocular设备来说，这个值是: Android
     * @return 响应用户的获取设备信息的请求
     */
    private String constructOnvifDevInfoResponse(String manufacture,
                                                 String model,
                                                 String firmwareVersion,
                                                 String serialNum,
                                                 String hardwareID) {
        Log.d(TAG, String.format("device manufacture : %s, device model: %s, device firmware version: %s," +
                        "device serial number: %s, device hardware ID: %s", manufacture, model, firmwareVersion,
                serialNum, hardwareID));

        String response = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<env:Envelope xmlns:env=\"http://www.w3.org/2003/05/soap-envelope\"\n" +
                "    xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\">\n" +
                "    <env:Body>\n" +
                "        <tds:GetDeviceInformationResponse>\n" +
                "            <tds:Manufacturer>" + manufacture + "</tds:Manufacturer>\n" +
                "            <tds:Model>" + model + "</tds:Model>\n" +
                "            <tds:FirmwareVersion>" + firmwareVersion + "</tds:FirmwareVersion>\n" +
                "            <tds:SerialNumber>" + serialNum + "</tds:SerialNumber>\n" +
                "            <tds:HardwareId>" + hardwareID + "</tds:HardwareId>\n" +
                "        </tds:GetDeviceInformationResponse>\n" +
                "    </env:Body>\n" +
                "</env:Envelope>";

        return response;
    }

    private String constructOnvifGetProfilesResponse(final int videoWidth, final int videoHeight, final int bitRate) {
        Log.d(TAG, String.format("the profile : video width : %s, video height : %s, video bitrate : %s", videoWidth,
                videoHeight, bitRate));
        // TODO: 这里的信息需要更加准确的控制
        // 关于分辨率的信息，应该从RtspServer当中动态获取
        String response = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<env:Envelope xmlns:env=\"http://www.w3.org/2003/05/soap-envelope\"\n" +
                "    xmlns:trt=\"http://www.onvif.org/ver10/media/wsdl\"\n" +
                "    xmlns:tt=\"http://www.onvif.org/ver10/schema\">\n" +
                "    <env:Body>\n" +
                "        <trt:GetProfilesResponse>\n" +
                "            <trt:Profiles\n" +
                "                fixed=\"false\"\n" +
                "                token=\"Profile1\">\n" +
                "                <tt:Name>Profile1</tt:Name>\n" +
                "                <tt:VideoSourceConfiguration token=\"VideoSourceConfiguration0_0\">\n" +
                "                    <tt:Name>VideoSourceConfiguration0_0</tt:Name>\n" +
                "                    <tt:UseCount>1</tt:UseCount>\n" +
                "                    <tt:SourceToken>VideoSource0</tt:SourceToken>\n" +
                "                    <tt:Bounds\n" +
                "                        height=\"" + videoHeight + "\"\n" +
                "                        width=\"" + videoWidth + "\"\n" +
                "                        x=\"0\"\n" +
                "                        y=\"0\" />\n" +
                "                </tt:VideoSourceConfiguration>\n" +
                "                <tt:VideoEncoderConfiguration token=\"VideoEncoderConfiguration0_0\">\n" +
                "                    <tt:Name>VideoEncoderConfiguration0_0</tt:Name>\n" +
                "                    <tt:UseCount>3683892</tt:UseCount>\n" +
                "                    <tt:Encoding>H264</tt:Encoding>\n" +
                "                    <tt:Resolution>\n" +
                "                        <tt:Width>" + videoWidth + "</tt:Width>\n" +
                "                        <tt:Height>" + videoHeight + "</tt:Height>\n" +
                "                    </tt:Resolution>\n" +
                "                    <tt:Quality>44.0</tt:Quality>\n" +
                "                    <tt:RateControl>\n" +
                "                        <tt:FrameRateLimit>5</tt:FrameRateLimit>\n" +
                "                        <tt:EncodingInterval>1</tt:EncodingInterval>\n" +
                "                        <tt:BitrateLimit>" + bitRate + "</tt:BitrateLimit>\n" +
                "                    </tt:RateControl>\n" +
                "                    <tt:Multicast>\n" +
                "                        <tt:Address>\n" +
                "                            <tt:Type>IPv4</tt:Type>\n" +
                "                            <tt:IPv4Address>0.0.0.0</tt:IPv4Address>\n" +
                "                            <tt:IPv6Address />\n" +
                "                        </tt:Address>\n" +
                "                        <tt:Port>0</tt:Port>\n" +
                "                        <tt:TTL>0</tt:TTL>\n" +
                "                        <tt:AutoStart>false</tt:AutoStart>\n" +
                "                    </tt:Multicast>\n" +
                "                    <tt:SessionTimeout>PT30S</tt:SessionTimeout>\n" +
                "                </tt:VideoEncoderConfiguration>\n" +
                "            </trt:Profiles>\n" +
                "        </trt:GetProfilesResponse>\n" +
                "    </env:Body>\n" +
                "</env:Envelope>";

        return response;
    }

    /**
     * @param rtspUrl 用于观看视频直播的rtsp地址，例如对于Ocular应用来说，他返回给IPCamera-Viewer
     *                的地址就是:rtsp://172.16.0.50:8081:8081/h264
     *                当然不同的应用可以定义不同格式的地址.
     * @return 返回给客户端用于播放rtsp直播视频流的url
     */
    private String constructOnvifStreamUriResponse(String rtspUrl) {
        Log.d(TAG, "the stream uri return to client are : " + rtspUrl);

        String response = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<env:Envelope xmlns:env=\"http://www.w3.org/2003/05/soap-envelope\"\n" +
                "    xmlns:trt=\"http://www.onvif.org/ver10/media/wsdl\"\n" +
                "    xmlns:tt=\"http://www.onvif.org/ver10/schema\">\n" +
                "    <env:Body>\n" +
                "        <trt:GetStreamUriResponse>\n" +
                "            <trt:MediaUri>\n" +
                "                <tt:Uri>" + rtspUrl + "</tt:Uri>\n" +
                "                <tt:InvalidAfterConnect>false</tt:InvalidAfterConnect>\n" +
                "                <tt:InvalidAfterReboot>false</tt:InvalidAfterReboot>\n" +
                "                <tt:Timeout>P1Y</tt:Timeout>\n" +
                "            </trt:MediaUri>\n" +
                "        </trt:GetStreamUriResponse>\n" +
                "    </env:Body>\n" +
                "</env:Envelope>";

        return response;
    }


}
