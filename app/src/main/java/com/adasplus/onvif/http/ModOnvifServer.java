package com.adasplus.onvif.http;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.adasplus.onvif.ONVIFPacketHandler;
import com.adasplus.onvif.ONVIFReqPacketHeader;
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
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URLDecoder;
import java.sql.Time;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

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

    private static final String REQUEST_DEVICE_SERVICE = "/onvif/device_service";
    private static final String REQUEST_ = "";

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
        Calendar calendar = Calendar.getInstance();
        final int year = calendar.get(Calendar.YEAR);
        final int month = calendar.get(Calendar.MONTH) + 1;
        final int day = calendar.get(Calendar.DAY_OF_MONTH);
        final int hour = calendar.get(Calendar.HOUR_OF_DAY);
        final int minutes = calendar.get(Calendar.MINUTE);
        final int seconds = calendar.get(Calendar.SECOND);

        String serverIp = Utilities.getLocalDevIp(mContext);

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
            if (requestContent.contains("GetSystemDateAndTime")) {
                Log.d(TAG, "Handle the response of GetSystemDateAndTime request");
                TimeZone timeZone = TimeZone.getDefault();
                String timeZoneName = timeZone.getDisplayName() + timeZone.getID();

                String getSystemDateNTimeResponse = constructOnvifGetSystemDateNTimeResponse(timeZoneName,
                        year, month, day, hour, minutes, seconds);
                byte[] responseContentByteArr = getSystemDateNTimeResponse.getBytes("UTF-8");
                InputStream servicesResponseInputStream = new ByteArrayInputStream(responseContentByteArr);
                body = new InputStreamEntity(servicesResponseInputStream, responseContentByteArr.length);
            } else if (requestContent.contains("GetCapabilities")) {
                Log.d(TAG, "Handle the response of GetCapabilities request");
                // we need parse the request, and parse out the required field we need
                SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
                SAXParser saxParser = null;
                try {
                    saxParser = saxParserFactory.newSAXParser();
                } catch (ParserConfigurationException e) {
                    Log.e(TAG, "SAX Parse Configuration Exception happened", e);
                } catch (SAXException e) {
                    Log.e(TAG, "SAX Exception happened", e);
                }
                if (saxParser == null) {
                    Log.e(TAG, "fail to get the SAX Parser, neglect this discovery packet");
                    return;
                }

                ONVIFPacketHandler handler = new ONVIFPacketHandler();
                try {
                    saxParser.parse(new ByteArrayInputStream(requestContent.getBytes()), handler);
                } catch (SAXException e) {
                    Log.e(TAG, "SAXException happened while processing the received message", e);
                } catch (IOException e) {
                    Log.e(TAG, "IOException happened while processing the received message", e);
                }
                ONVIFReqPacketHeader reqHeader = handler.getReqHeader();

                String userName = reqHeader.getUserName();
                String passwordDigest = reqHeader.getUserPsw();
                String nonce = reqHeader.getCapabilitiesNonce();
                String createdTime = year + "-" + month + "-" + day + "T" + hour + ":" + minutes + ":" + seconds + "Z";

                String getCapabilitiesResponse = constructOnvifGetCapabilitiesResponse(userName,
                        passwordDigest, nonce, createdTime, serverIp);
                byte[] responseContentByteArr = getCapabilitiesResponse.getBytes("UTF-8");
                InputStream servicesResponseInputStream = new ByteArrayInputStream(responseContentByteArr);
                body = new InputStreamEntity(servicesResponseInputStream, responseContentByteArr.length);
            } else if (requestContent.contains("GetServices")) {
                Log.d(TAG, "is GetServices interface");
                Log.d(TAG, "return the device base info");
                String getServicesResponse = constructOnvifDeviceServiceResponse(serverIp);
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
                String rtspServerUrl = "rtsp://" + serverIp + ":" + mRtspServerPort + "/";
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

    /**
     * Using to respond the request of /onvif/device_service "GetSystemDateAndTime"
     *
     * @return the constructed response content
     */
    private String constructOnvifGetSystemDateNTimeResponse(String timezone, int hour, int minute, int second,
                                                            int year, int month, int day) {
        Log.d(TAG, "handle the GetSystemDateAndTime response with timezone of " + timezone);

        String response = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<env:Envelope xmlns:env=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\" xmlns:trt=\"http://www.onvif.org/ver10/media/wsdl\" xmlns:tt=\"http://www.onvif.org/ver10/schema\">\n" +
                "    <env:Body>\n" +
                "        <tds:GetSystemDateAndTimeResponse>\n" +
                "            <tds:SystemDateAndTime>\n" +
                "                <tt:DateTimeType>Manual</tt:DateTimeType>\n" +
                "                <tt:DaylightSavings>false</tt:DaylightSavings>\n" +
                "                <tt:TimeZone>\n" +
                "                    <tt:TZ>" + timezone + "</tt:TZ>\n" +
                "                </tt:TimeZone>\n" +
                "                <tt:UTCDateTime>\n" +
                "                    <tt:Time>\n" +
                "                        <tt:Hour>" + hour + "</tt:Hour>\n" +
                "                        <tt:Minute>" + minute + "</tt:Minute>\n" +
                "                        <tt:Second>" + second + "</tt:Second>\n" +
                "                    </tt:Time>\n" +
                "                    <tt:Date>\n" +
                "                        <tt:Year>" + year + "</tt:Year>\n" +
                "                        <tt:Month>" + month + "</tt:Month>\n" +
                "                        <tt:Day>" + day + "</tt:Day>\n" +
                "                    </tt:Date>\n" +
                "                </tt:UTCDateTime>\n" +
                "            </tds:SystemDateAndTime>\n" +
                "        </tds:GetSystemDateAndTimeResponse>\n" +
                "    </env:Body>\n" +
                "</env:Envelope>";

        return response;
    }


    /**
     * construct the GetCapabilities response
     *
     * @param userName        eg. admin
     * @param passwordDigest  eg. 5y9Zcgk2iMChJdQw/yOKEtqsI/0=
     * @param nonce           eg. Nzg1MDE2OTYz
     * @param createdTime     eg. 2014-12-24T16:20:37.475Z
     * @param serverIpAddress eg. 192.168.100.110
     * @return the GetCapabilitiesResponse
     */
    private String constructOnvifGetCapabilitiesResponse(String userName, String passwordDigest, String nonce,
                                                         String createdTime, String serverIpAddress) {
        Log.d(TAG, "respond the GetCapabilities request with userName " + userName + ", password digest " + passwordDigest
                + ", nonce " + nonce + ", created time " + createdTime + ", server ip are " + serverIpAddress);

        String response = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:SOAP-ENC=\"http://www.w3.org/2003/05/soap-encoding\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:chan=\"http://schemas.microsoft.com/ws/2005/02/duplex\" xmlns:wsa5=\"http://www.w3.org/2005/08/addressing\" xmlns:c14n=\"http://www.w3.org/2001/10/xml-exc-c14n#\" xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\" xmlns:xenc=\"http://www.w3.org/2001/04/xmlenc#\" xmlns:wsc=\"http://schemas.xmlsoap.org/ws/2005/02/sc\" xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\" xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" xmlns:xmime5=\"http://www.w3.org/2005/05/xmlmime\" xmlns:xmime=\"http://tempuri.org/xmime.xsd\" xmlns:xop=\"http://www.w3.org/2004/08/xop/include\" xmlns:tt=\"http://www.onvif.org/ver10/schema\" xmlns:wsrfbf=\"http://docs.oasis-open.org/wsrf/bf-2\" xmlns:wstop=\"http://docs.oasis-open.org/wsn/t-1\" xmlns:wsrfr=\"http://docs.oasis-open.org/wsrf/r-2\" xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\" xmlns:tev=\"http://www.onvif.org/ver10/events/wsdl\" xmlns:wsnt=\"http://docs.oasis-open.org/wsn/b-2\" xmlns:tptz=\"http://www.onvif.org/ver20/ptz/wsdl\" xmlns:trt=\"http://www.onvif.org/ver10/media/wsdl\" xmlns:timg=\"http://www.onvif.org/ver20/imaging/wsdl\" xmlns:tmd=\"http://www.onvif.org/ver10/deviceIO/wsdl\" xmlns:tns1=\"http://www.onvif.org/ver10/topics\" xmlns:ter=\"http://www.onvif.org/ver10/error\" xmlns:tnsaxis=\"http://www.axis.com/2009/event/topics\">\n" +
                "    <SOAP-ENV:Header>\n" +
                "        <wsse:Security>\n" +
                "            <wsse:UsernameToken>\n" +
                "                <wsse:Username>" + userName + "</wsse:Username>\n" +
                "                <wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest\">" + passwordDigest + "</wsse:Password>\n" +
                "                <wsse:Nonce>" + nonce + "</wsse:Nonce>\n" +
                "                <wsu:Created>" + createdTime + "</wsu:Created>\n" +
                "            </wsse:UsernameToken>\n" +
                "        </wsse:Security>\n" +
                "    </SOAP-ENV:Header>\n" +
                "    <SOAP-ENV:Body>\n" +
                "        <tds:GetCapabilitiesResponse>\n" +
                "            <tds:Capabilities>\n" +
                "                <tt:Device>\n" +
                "                    <tt:XAddr>http://" + serverIpAddress + "/onvif/services</tt:XAddr>\n" +
                "                    <tt:Network>\n" +
                "                        <tt:IPFilter>false</tt:IPFilter>\n" +
                "                        <tt:ZeroConfiguration>true</tt:ZeroConfiguration>\n" +
                "                        <tt:IPVersion6>false</tt:IPVersion6>\n" +
                "                        <tt:DynDNS>false</tt:DynDNS>\n" +
                "                    </tt:Network>\n" +
                "                    <tt:System>\n" +
                "                        <tt:DiscoveryResolve>false</tt:DiscoveryResolve>\n" +
                "                        <tt:DiscoveryBye>false</tt:DiscoveryBye>\n" +
                "                        <tt:RemoteDiscovery>false</tt:RemoteDiscovery>\n" +
                "                        <tt:SystemBackup>false</tt:SystemBackup>\n" +
                "                        <tt:SystemLogging>false</tt:SystemLogging>\n" +
                "                        <tt:FirmwareUpgrade>false</tt:FirmwareUpgrade>\n" +
                "                        <tt:SupportedVersions>\n" +
                "                            <tt:Major>2</tt:Major>\n" +
                "                            <tt:Minor>40</tt:Minor>\n" +
                "                        </tt:SupportedVersions>\n" +
                "                    </tt:System>\n" +
                "                    <tt:IO>\n" +
                "                        <tt:InputConnectors>1</tt:InputConnectors>\n" +
                "                        <tt:RelayOutputs>1</tt:RelayOutputs>\n" +
                "                    </tt:IO>\n" +
                "                    <tt:Security>\n" +
                "                        <tt:TLS1.1>true</tt:TLS1.1>\n" +
                "                        <tt:TLS1.2>false</tt:TLS1.2>\n" +
                "                        <tt:OnboardKeyGeneration>false</tt:OnboardKeyGeneration>\n" +
                "                        <tt:AccessPolicyConfig>false</tt:AccessPolicyConfig>\n" +
                "                        <tt:X.509Token>false</tt:X.509Token>\n" +
                "                        <tt:SAMLToken>false</tt:SAMLToken>\n" +
                "                        <tt:KerberosToken>false</tt:KerberosToken>\n" +
                "                        <tt:RELToken>false</tt:RELToken>\n" +
                "                    </tt:Security>\n" +
                "                </tt:Device>\n" +
                "                <tt:Events>\n" +
                "                    <tt:XAddr>http://" + serverIpAddress + "/onvif/services</tt:XAddr>\n" +
                "                    <tt:WSSubscriptionPolicySupport>false</tt:WSSubscriptionPolicySupport>\n" +
                "                    <tt:WSPullPointSupport>false</tt:WSPullPointSupport>\n" +
                "                    <tt:WSPausableSubscriptionManagerInterfaceSupport>false</tt:WSPausableSubscriptionManagerInterfaceSupport>\n" +
                "                </tt:Events>\n" +
                "                <tt:Imaging>\n" +
                "                    <tt:XAddr>http://" + serverIpAddress + "/onvif/services</tt:XAddr>\n" +
                "                </tt:Imaging>\n" +
                "                <tt:Media>\n" +
                "                    <tt:XAddr>http://" + serverIpAddress + "/onvif/services</tt:XAddr>\n" +
                "                    <tt:StreamingCapabilities>\n" +
                "                        <tt:RTPMulticast>false</tt:RTPMulticast>\n" +
                "                        <tt:RTP_TCP>true</tt:RTP_TCP>\n" +
                "                        <tt:RTP_RTSP_TCP>true</tt:RTP_RTSP_TCP>\n" +
                "                    </tt:StreamingCapabilities>\n" +
                "                </tt:Media>\n" +
                "                <tt:PTZ>\n" +
                "                    <tt:XAddr>http://" + serverIpAddress + "/onvif/services</tt:XAddr>\n" +
                "                </tt:PTZ>\n" +
                "                <tt:Extension>\n" +
                "                    <tt:DeviceIO>\n" +
                "                        <tt:XAddr>http://" + serverIpAddress + "/onvif/services</tt:XAddr>\n" +
                "                        <tt:VideoSources>1</tt:VideoSources>\n" +
                "                        <tt:VideoOutputs>0</tt:VideoOutputs>\n" +
                "                        <tt:AudioSources>1</tt:AudioSources>\n" +
                "                        <tt:AudioOutputs>0</tt:AudioOutputs>\n" +
                "                        <tt:RelayOutputs>1</tt:RelayOutputs>\n" +
                "                    </tt:DeviceIO>\n" +
                "                </tt:Extension>\n" +
                "            </tds:Capabilities>\n" +
                "        </tds:GetCapabilitiesResponse>\n" +
                "    </SOAP-ENV:Body>\n" +
                "</SOAP-ENV:Envelope>";

        return response;
    }


}
