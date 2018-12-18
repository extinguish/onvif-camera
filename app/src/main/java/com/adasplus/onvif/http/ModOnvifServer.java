package com.adasplus.onvif.http;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
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

    private int clientRequiredHour, clientRequiredMinute, clientRequiredSecond, clientRequiredYear, clientRequiredMonth, clientRequiredDay = 0;

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

            String requestContent = new String(entityContent);

            try {
                saxParser.parse(new ByteArrayInputStream(requestContent.getBytes()), handler);
            } catch (SAXException e) {
                Log.e(TAG, "SAXException happened while processing the received message", e);
            } catch (IOException e) {
                Log.e(TAG, "IOException happened while processing the received message", e);
            }
            ONVIFReqPacketHeader reqHeader = handler.getReqHeader();

            // 关于不同的请求内容对应的不同的含义，直接参考位于项目根目录当中的ONVIF_Protocol.md
            // 以下的请求结果返回都是/onvif/device_service接口的返回数据
            httpResponse.setStatusCode(HttpStatus.SC_OK);
            if (requestContent.contains("GetSystemDateAndTime")) {
                Log.d(TAG, "Handle the response of GetSystemDateAndTime request");
                TimeZone timeZone = TimeZone.getDefault();
                String timeZoneName = timeZone.getDisplayName(Locale.US) + timeZone.getID();

//                if (clientRequiredYear == 0) {
//                    clientRequiredYear = year;
//                }
//                if (clientRequiredMonth == 0) {
//                    clientRequiredMonth = month;
//                }
//                if (clientRequiredDay == 0) {
//                    clientRequiredDay = day;
//                }
//                if (clientRequiredHour == 0) {
//                    clientRequiredHour = hour;
//                }
//                if (clientRequiredMinute == 0) {
//                    clientRequiredMinute = minutes;
//                }
//                if (clientRequiredSecond == 0) {
//                    clientRequiredSecond = seconds;
//                }

                String getSystemDateNTimeResponse = constructOnvifGetSystemDateNTimeResponse(timeZoneName,
                        hour, minutes, seconds, year, month, day);
                byte[] responseContentByteArr = getSystemDateNTimeResponse.getBytes("UTF-8");
                InputStream servicesResponseInputStream = new ByteArrayInputStream(responseContentByteArr);
                body = new InputStreamEntity(servicesResponseInputStream, responseContentByteArr.length);
            } else if (requestContent.contains("GetCapabilities")) {
                Log.d(TAG, "Handle the response of GetCapabilities request");

                String userName = reqHeader.getUserName();
                String passwordDigest = reqHeader.getUserPsw();
                String nonce = reqHeader.getCapabilitiesNonce();
                String createdTime = year + "-" + month + "-" + day + "T" + hour + ":" + minutes + ":" + seconds + "Z";

                // generate the auth string based on user request password

                String getCapabilitiesResponse = constructOnvifGetCapabilitiesResponse(serverIp);
                byte[] responseContentByteArr = getCapabilitiesResponse.getBytes("UTF-8");
                InputStream servicesResponseInputStream = new ByteArrayInputStream(responseContentByteArr);
                body = new InputStreamEntity(servicesResponseInputStream, responseContentByteArr.length);
            } else if (requestContent.contains("SetSystemDateAndTime")) {
                boolean setupResult = false;
                try {
                    // parse out the detailed field the client provided
                    int clientHour = Integer.parseInt(reqHeader.getHour());
                    int clientMinute = Integer.parseInt(reqHeader.getMinute());
                    int clientSecond = Integer.parseInt(reqHeader.getSecond());
                    int clientYear = Integer.parseInt(reqHeader.getYear());
                    int clientMonth = Integer.parseInt(reqHeader.getMonth());
                    int clientDay = Integer.parseInt(reqHeader.getDay());
                    // TODO: we store these values, and use for the corresponding GetSystemDateAndTime request
//                    clientRequiredYear = clientYear;
//                    clientRequiredMonth = clientMonth;
//                    clientRequiredDay = clientDay;
//                    clientRequiredMinute = clientMinute;
//                    clientRequiredHour = clientHour;
//                    clientRequiredSecond = clientSecond;

                    // and we using the client provided time to sync current device's time
                    // but if we are not system app, then we will do not have permission to setup system date
                    // and time
                    Calendar syncTimeCalendar = Calendar.getInstance();
                    syncTimeCalendar.set(clientYear, clientMonth, clientDay, clientHour, clientMinute,
                            clientSecond);
                    AlarmManager alarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
                    if (alarmManager != null) {
                        alarmManager.setTime(syncTimeCalendar.getTimeInMillis());
                        setupResult = true;
                    }
                } catch (final Exception e) {
                    Log.e(TAG, "Exception happened while setup the system date and time");
                }

                // TODO: as we do not have the permission to setup system date and time, so we just
                // returns true, and make cheat on the onvif client
                String setDateAndTimeResponse = constructOnvifSetSystemDateAndTimeResponse(setupResult);
                byte[] responseContentByteArr = setDateAndTimeResponse.getBytes("UTF-8");
                InputStream responseInputStream = new ByteArrayInputStream(responseContentByteArr);
                body = new InputStreamEntity(responseInputStream, responseContentByteArr.length);
            } else if (requestContent.contains("GetVideoEncoderConfigurationOptions")) {
                Log.d(TAG, "received the GetVideoEncoderConfigurationOptions request");
                final int width = 320;
                final int height = 240;
                String getVideoEncoderConfigOpsResponse = constructOnvifGetVideoEncoderConfigurationOptionsResponse(width, height);
                byte[] responseContentByteArr = getVideoEncoderConfigOpsResponse.getBytes("UTF-8");
                InputStream videoEncoderConfigOptsResponseStream = new ByteArrayInputStream(responseContentByteArr);
                body = new InputStreamEntity(videoEncoderConfigOptsResponseStream, responseContentByteArr.length);
            } else if (requestContent.contains("CreateOSD")) {
                Log.d(TAG, "handle the CreateOSD request");

                String createOSDResponse = constructOnvifCreateOSDResponse("osdToken_01");
                byte[] responseByteArr = createOSDResponse.getBytes("UTF-8");
                InputStream responseInputStream = new ByteArrayInputStream(responseByteArr);
                body = new InputStreamEntity(responseInputStream, responseByteArr.length);
            } else if (requestContent.contains("SetOSD")) {
                Log.d(TAG, "handle the SetOSD request");
                String setOSDResponse = constructOnvifSetOSDResponse();
                byte[] responseByteArr = setOSDResponse.getBytes("UTF-8");
                InputStream responseInputStream = new ByteArrayInputStream(responseByteArr);
                body = new InputStreamEntity(responseInputStream, responseByteArr.length);
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
                // TODO: the following video attributes value should be retrieved from the native IPCamera dynamically,
                // as these information may be changing with different device
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
                Log.e(TAG, "not known interface of --> " + requestContent);
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
            body.setContentType("application/soap+xml");
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
        Log.d(TAG, "construct get service response with ip address of " + localIpAddress);
        String responseContent = "<?xml version='1.0' encoding='utf-8' ?>\n" +
                "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\"\n" +
                "xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\"\n" +
                "xmlns:tev=\"http://www.onvif.org/ver10/events/wsdl\"\n" +
                "xmlns:timg=\"http://www.onvif.org/ver20/imaging/wsdl\"\n" +
                "xmlns:tptz=\"http://www.onvif.org/ver20/ptz/wsdl\"\n" +
                "xmlns:trt=\"http://www.onvif.org/ver10/media/wsdl\"\n" +
                "xmlns:tt=\"http://www.onvif.org/ver10/schema\">\n" +
                "<s:Body>\n" +
                "<tds:GetServicesResponse>\n" +
                "<tds:Service>\n" +
                "<tds:Namespace>http://www.onvif.org/ver10/device/wsdl</tds:Namespace>\n" +
                "<tds:XAddr>http://" + localIpAddress + ":8080/onvif/device_service</tds:XAddr>\n" +
                "<tds:Capabilities>\n" +
                "<tds:Capabilities>\n" +
                "<tds:Network\n" +
                "DHCPv6=\"false\"\n" +
                "Dot11Configuration=\"false\"\n" +
                "Dot1XConfigurations=\"0\"\n" +
                "DynDNS=\"false\"\n" +
                "HostnameFromDHCP=\"true\"\n" +
                "IPFilter=\"true\"\n" +
                "IPVersion6=\"false\"\n" +
                "NTP=\"1\"\n" +
                "ZeroConfiguration=\"true\" />\n" +
                "<tds:Security\n" +
                "AccessPolicyConfig=\"true\"\n" +
                "DefaultAccessPolicy=\"false\"\n" +
                "Dot1X=\"false\"\n" +
                "HttpDigest=\"false\"\n" +
                "KerberosToken=\"false\"\n" +
                "MaxUsers=\"10\"\n" +
                "OnboardKeyGeneration=\"false\"\n" +
                "RELToken=\"false\"\n" +
                "RemoteUserHandling=\"false\"\n" +
                "SAMLToken=\"false\"\n" +
                "TLS1.0=\"false\"\n" +
                "TLS1.1=\"false\"\n" +
                "TLS1.2=\"false\"\n" +
                "UsernameToken=\"true\"\n" +
                "X.509Token=\"false\" />\n" +
                "<tds:System\n" +
                "DiscoveryBye=\"true\"\n" +
                "DiscoveryResolve=\"true\"\n" +
                "FirmwareUpgrade=\"false\"\n" +
                "HttpFirmwareUpgrade=\"false\"\n" +
                "HttpSupportInformation=\"false\"\n" +
                "HttpSystemBackup=\"false\"\n" +
                "HttpSystemLogging=\"false\"\n" +
                "RemoteDiscovery=\"false\"\n" +
                "SystemBackup=\"false\"\n" +
                "SystemLogging=\"false\" />\n" +
                "</tds:Capabilities>\n" +
                "</tds:Capabilities>\n" +
                "<tds:Version>\n" +
                "<tt:Major>1</tt:Major>\n" +
                "<tt:Minor>70</tt:Minor>\n" +
                "</tds:Version>\n" +
                "</tds:Service>\n" +
                "<tds:Service>\n" +
                "<tds:Namespace>http://www.onvif.org/ver10/events/wsdl</tds:Namespace>\n" +
                "<tds:XAddr>http://" + localIpAddress + ":8080/event/evtservice</tds:XAddr>\n" +
                "<tds:Capabilities>\n" +
                "<tev:Capabilities\n" +
                "MaxNotificationProducers=\"6\"\n" +
                "MaxPullPoints=\"2\"\n" +
                "PersistentNotificationStorage=\"false\"\n" +
                "WSPausableSubscriptionManagerInterfaceSupport=\"false\"\n" +
                "WSPullPointSupport=\"false\"\n" +
                "WSSubscriptionPolicySupport=\"false\" />\n" +
                "</tds:Capabilities>\n" +
                "<tds:Version>\n" +
                "<tt:Major>1</tt:Major>\n" +
                "<tt:Minor>70</tt:Minor>\n" +
                "</tds:Version>\n" +
                "</tds:Service>\n" +
                "<tds:Service>\n" +
                "<tds:Namespace>http://www.onvif.org/ver20/imaging/wsdl</tds:Namespace>\n" +
                "<tds:XAddr>http://" + localIpAddress + ":8080/onvif/imaging</tds:XAddr>\n" +
                "<tds:Capabilities>\n" +
                "<timg:Capabilities ImageStabilization=\"false\" />\n" +
                "</tds:Capabilities>\n" +
                "<tds:Version>\n" +
                "<tt:Major>2</tt:Major>\n" +
                "<tt:Minor>30</tt:Minor>\n" +
                "</tds:Version>\n" +
                "</tds:Service>\n" +
                "<tds:Service>\n" +
                "<tds:Namespace>http://www.onvif.org/ver10/media/wsdl</tds:Namespace>\n" +
                "<tds:XAddr>http://" + localIpAddress + ":8080/onvif/media</tds:XAddr>\n" +
                "<tds:Capabilities>\n" +
                "<trt:Capabilities\n" +
                "OSD=\"false\"\n" +
                "Rotation=\"false\"\n" +
                "SnapshotUri=\"true\"\n" +
                "VideoSourceMode=\"false\">\n" +
                "<trt:ProfileCapabilities MaximumNumberOfProfiles=\"10\" />\n" +
                "<trt:StreamingCapabilities\n" +
                "NoRTSPStreaming=\"false\"\n" +
                "NonAggregateControl=\"false\"\n" +
                "RTPMulticast=\"false\"\n" +
                "RTP_RTSP_TCP=\"true\"\n" +
                "RTP_TCP=\"false\" />\n" +
                "</trt:Capabilities>\n" +
                "</tds:Capabilities>\n" +
                "<tds:Version>\n" +
                "<tt:Major>1</tt:Major>\n" +
                "<tt:Minor>70</tt:Minor>\n" +
                "</tds:Version>\n" +
                "</tds:Service>\n" +
                "<tds:Service>\n" +
                "<tds:Namespace>http://www.onvif.org/ver20/ptz/wsdl</tds:Namespace>\n" +
                "<tds:XAddr>http://" + localIpAddress + ":8080/onvif/ptz</tds:XAddr>\n" +
                "<tds:Capabilities>\n" +
                "<tptz:Capabilities\n" +
                "EFlip=\"false\"\n" +
                "GetCompatibleConfigurations=\"false\"\n" +
                "Reverse=\"false\" />\n" +
                "</tds:Capabilities>\n" +
                "<tds:Version>\n" +
                "<tt:Major>2</tt:Major>\n" +
                "<tt:Minor>50</tt:Minor>\n" +
                "</tds:Version>\n" +
                "</tds:Service>\n" +
                "</tds:GetServicesResponse>\n" +
                "</s:Body>\n" +
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
                "<env:Envelope xmlns:env=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\">\n" +
                "<env:Body>\n" +
                "<tds:GetDeviceInformationResponse>\n" +
                "<tds:Manufacturer>" + manufacture + "</tds:Manufacturer>\n" +
                "<tds:Model>" + model + "</tds:Model>\n" +
                "<tds:FirmwareVersion>" + firmwareVersion + "</tds:FirmwareVersion>\n" +
                "<tds:SerialNumber>" + serialNum + "</tds:SerialNumber>\n" +
                "<tds:HardwareId>" + hardwareID + "</tds:HardwareId>\n" +
                "</tds:GetDeviceInformationResponse>\n" +
                "</env:Body>\n" +
                "</env:Envelope>";

        return response;
    }

    private String constructOnvifGetProfilesResponse(final int videoWidth, final int videoHeight, final int bitRate) {
        Log.d(TAG, String.format("the profile : video width : %s, video height : %s, video bitrate : %s", videoWidth,
                videoHeight, bitRate));
        // TODO: 这里的信息需要更加准确的控制
        // 关于分辨率的信息，应该从RtspServer当中动态获取
        String response = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<SOAP-ENV:Envelope xmlns:SOAP-ENC=\"http://www.w3.org/2003/05/soap-encoding\" xmlns:SOAP-ENV=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:c14n=\"http://www.w3.org/2001/10/xml-exc-c14n#\" xmlns:chan=\"http://schemas.microsoft.com/ws/2005/02/duplex\" xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\" xmlns:saml1=\"urn:oasis:names:tc:SAML:1.0:assertion\" xmlns:saml2=\"urn:oasis:names:tc:SAML:2.0:assertion\" xmlns:tdn=\"http://www.onvif.org/ver10/network/wsdl\" xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\" xmlns:trt=\"http://www.onvif.org/ver10/media/wsdl\" xmlns:tt=\"http://www.onvif.org/ver10/schema\" xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" xmlns:wsa5=\"http://www.w3.org/2005/08/addressing\" xmlns:wsc=\"http://docs.oasis-open.org/ws-sx/ws-secureconversation/200512\" xmlns:wsdd=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\" xmlns:wsnt=\"http://docs.oasis-open.org/wsn/b-2\" xmlns:wsrfbf=\"http://docs.oasis-open.org/wsrf/bf-2\" xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" xmlns:wstop=\"http://docs.oasis-open.org/wsn/t-1\" xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\" xmlns:xenc=\"http://www.w3.org/2001/04/xmlenc#\" xmlns:xmime=\"http://tempuri.org/xmime.xsd\" xmlns:xop=\"http://www.w3.org/2004/08/xop/include\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "<SOAP-ENV:Body>\n" +
                "<trt:GetProfilesResponse>\n" +
                "<trt:Profiles fixed=\"false\" token=\"profile1\">\n" +
                "<tt:Name>profile1</tt:Name>\n" +
                "<tt:VideoSourceConfiguration token=\"source_config\">\n" +
                "<tt:Name>stream1_main</tt:Name>\n" +
                "<tt:UseCount>2</tt:UseCount>\n" +
                "<tt:SourceToken>VideoSource1</tt:SourceToken>\n" +
                "<tt:Bounds height=\"" + videoHeight + "\" width=\"" + videoWidth + "\" x=\"0\" y=\"0\"></tt:Bounds>\n" +
                "</tt:VideoSourceConfiguration>\n" +
                "<tt:AudioSourceConfiguration token=\"AudioSourceConfigToken\">\n" +
                "<tt:Name>AudioSourceConfig</tt:Name>\n" +
                "<tt:UseCount>2</tt:UseCount>\n" +
                "<tt:SourceToken>AudioSourceChannel</tt:SourceToken>\n" +
                "</tt:AudioSourceConfiguration>\n" +
                "<tt:VideoEncoderConfiguration token=\"encoder_config\">\n" +
                "<tt:Name>encoder_config</tt:Name>\n" +
                "<tt:UseCount>2</tt:UseCount>\n" +
                "<tt:Encoding>H264</tt:Encoding>\n" +
                "<tt:Resolution>\n" +
                "<tt:Width>" + videoWidth + "</tt:Width>\n" +
                "<tt:Height>" + videoHeight + "</tt:Height>\n" +
                "</tt:Resolution>\n" +
                "<tt:Quality>2</tt:Quality>\n" +
                "<tt:RateControl>\n" +
                "<tt:FrameRateLimit>" + 15 + "</tt:FrameRateLimit>\n" +
                "<tt:EncodingInterval>0</tt:EncodingInterval>\n" +
                "<tt:BitrateLimit>" + bitRate + "</tt:BitrateLimit>\n" +
                "</tt:RateControl>\n" +
                "<tt:H264>\n" +
                "<tt:GovLength>0</tt:GovLength>\n" +
                "<tt:H264Profile>Baseline</tt:H264Profile>\n" +
                "</tt:H264>\n" +
                "<tt:Multicast>\n" +
                "<tt:Address>\n" +
                "<tt:Type>IPv4</tt:Type>\n" +
                "<tt:IPv4Address>0.0.0.0</tt:IPv4Address>\n" +
                "</tt:Address>\n" +
                "<tt:Port>6601</tt:Port>\n" +
                "<tt:TTL>64</tt:TTL>\n" +
                "<tt:AutoStart>false</tt:AutoStart>\n" +
                "</tt:Multicast>\n" +
                "<tt:SessionTimeout>PT00H01M00S</tt:SessionTimeout>\n" +
                "</tt:VideoEncoderConfiguration>\n" +
                "<tt:AudioEncoderConfiguration token=\"encoder_config\">\n" +
                "<tt:Name>encoder_config</tt:Name>\n" +
                "<tt:UseCount>2</tt:UseCount>\n" +
                "<tt:Encoding>G711</tt:Encoding>\n" +
                "<tt:Bitrate>20</tt:Bitrate>\n" +
                "<tt:SampleRate>8</tt:SampleRate>\n" +
                "<tt:Multicast>\n" +
                "<tt:Address>\n" +
                "<tt:Type>IPv4</tt:Type>\n" +
                "<tt:IPv4Address>0.0.0.0</tt:IPv4Address>\n" +
                "</tt:Address>\n" +
                "<tt:Port>6601</tt:Port>\n" +
                "<tt:TTL>64</tt:TTL>\n" +
                "<tt:AutoStart>false</tt:AutoStart>\n" +
                "</tt:Multicast>\n" +
                "<tt:SessionTimeout>PT00H01M00S</tt:SessionTimeout>\n" +
                "</tt:AudioEncoderConfiguration>\n" +
                "<tt:Extension>\n" +
                "<tt:AudioOutputConfiguration token=\"AudioOutputConfigToken\">\n" +
                "<tt:Name>AudioOutputConfigName</tt:Name>\n" +
                "<tt:UseCount>2</tt:UseCount>\n" +
                "<tt:OutputToken>AudioOutputToken</tt:OutputToken>\n" +
                "<tt:SendPrimacy>www.onvif.org/ver20/HalfDuplex/Server</tt:SendPrimacy>\n" +
                "<tt:OutputLevel>10</tt:OutputLevel>\n" +
                "</tt:AudioOutputConfiguration>\n" +
                "</tt:Extension>\n" +
                "</trt:Profiles>\n" +
                "<trt:Profiles fixed=\"false\" token=\"profile2\">\n" +
                "<tt:Name>profile2</tt:Name>\n" +
                "<tt:VideoSourceConfiguration token=\"source_config\">\n" +
                "<tt:Name>stream1_sub</tt:Name>\n" +
                "<tt:UseCount>2</tt:UseCount>\n" +
                "<tt:SourceToken>VideoSource2</tt:SourceToken>\n" +
                "<tt:Bounds height=\"" + videoHeight + "\" width=\"" + videoWidth + "\" x=\"0\" y=\"0\"></tt:Bounds>\n" +
                "</tt:VideoSourceConfiguration>\n" +
                "<tt:AudioSourceConfiguration token=\"AudioSourceConfigToken\">\n" +
                "<tt:Name>AudioSourceConfig</tt:Name>\n" +
                "<tt:UseCount>2</tt:UseCount>\n" +
                "<tt:SourceToken>AudioSourceChannel</tt:SourceToken>\n" +
                "</tt:AudioSourceConfiguration>\n" +
                "<tt:VideoEncoderConfiguration token=\"encoder_config\">\n" +
                "<tt:Name>encoder_config</tt:Name>\n" +
                "<tt:UseCount>2</tt:UseCount>\n" +
                "<tt:Encoding>H264</tt:Encoding>\n" +
                "<tt:Resolution>\n" +
                "<tt:Width>" + videoWidth + "</tt:Width>\n" +
                "<tt:Height>" + videoHeight + "</tt:Height>\n" +
                "</tt:Resolution>\n" +
                "<tt:Quality>2</tt:Quality>\n" +
                "<tt:RateControl>\n" +
                "<tt:FrameRateLimit>" + 15 + "</tt:FrameRateLimit>\n" +
                "<tt:EncodingInterval>0</tt:EncodingInterval>\n" +
                "<tt:BitrateLimit>" + bitRate + "</tt:BitrateLimit>\n" +
                "</tt:RateControl>\n" +
                "<tt:H264>\n" +
                "<tt:GovLength>0</tt:GovLength>\n" +
                "<tt:H264Profile>Baseline</tt:H264Profile>\n" +
                "</tt:H264>\n" +
                "<tt:Multicast>\n" +
                "<tt:Address>\n" +
                "<tt:Type>IPv4</tt:Type>\n" +
                "<tt:IPv4Address>0.0.0.0</tt:IPv4Address>\n" +
                "</tt:Address>\n" +
                "<tt:Port>6601</tt:Port>\n" +
                "<tt:TTL>64</tt:TTL>\n" +
                "<tt:AutoStart>false</tt:AutoStart>\n" +
                "</tt:Multicast>\n" +
                "<tt:SessionTimeout>PT00H01M00S</tt:SessionTimeout>\n" +
                "</tt:VideoEncoderConfiguration>\n" +
                "<tt:AudioEncoderConfiguration token=\"encoder_config\">\n" +
                "<tt:Name>encoder_config</tt:Name>\n" +
                "<tt:UseCount>2</tt:UseCount>\n" +
                "<tt:Encoding>G711</tt:Encoding>\n" +
                "<tt:Bitrate>20</tt:Bitrate>\n" +
                "<tt:SampleRate>8</tt:SampleRate>\n" +
                "<tt:Multicast>\n" +
                "<tt:Address>\n" +
                "<tt:Type>IPv4</tt:Type>\n" +
                "<tt:IPv4Address>0.0.0.0</tt:IPv4Address>\n" +
                "</tt:Address>\n" +
                "<tt:Port>6601</tt:Port>\n" +
                "<tt:TTL>64</tt:TTL>\n" +
                "<tt:AutoStart>false</tt:AutoStart>\n" +
                "</tt:Multicast>\n" +
                "<tt:SessionTimeout>PT00H01M00S</tt:SessionTimeout>\n" +
                "</tt:AudioEncoderConfiguration>\n" +
                "<tt:Extension>\n" +
                "<tt:AudioOutputConfiguration token=\"AudioOutputConfigToken\">\n" +
                "<tt:Name>AudioOutputConfigName</tt:Name>\n" +
                "<tt:UseCount>2</tt:UseCount>\n" +
                "<tt:OutputToken>AudioOutputToken</tt:OutputToken>\n" +
                "<tt:SendPrimacy>www.onvif.org/ver20/HalfDuplex/Server</tt:SendPrimacy>\n" +
                "<tt:OutputLevel>10</tt:OutputLevel>\n" +
                "</tt:AudioOutputConfiguration>\n" +
                "</tt:Extension>\n" +
                "</trt:Profiles>\n" +
                "</trt:GetProfilesResponse>\n" +
                "</SOAP-ENV:Body>\n" +
                "</SOAP-ENV:Envelope>";

        return response;
    }

    /**
     * @param rtspUrl 用于观看视频直播的rtsp地址，例如对于Ocular应用来说，他返回给IPCamera-Viewer
     *                的地址就是:rtsp://172.16.0.50:8086
     *                当然不同的应用可以定义不同格式的地址.
     * @return 返回给客户端用于播放rtsp直播视频流的url
     */
    private String constructOnvifStreamUriResponse(String rtspUrl) {
        Log.d(TAG, "the stream uri return to client are : " + rtspUrl);

        String response = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<env:Envelope xmlns:env=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:trt=\"http://www.onvif.org/ver10/media/wsdl\" xmlns:tt=\"http://www.onvif.org/ver10/schema\">\n" +
                "<env:Body>\n" +
                "<trt:GetStreamUriResponse>\n" +
                "<trt:MediaUri>\n" +
                "<tt:Uri>" + rtspUrl + "</tt:Uri>\n" +
                "<tt:InvalidAfterConnect>false</tt:InvalidAfterConnect>\n" +
                "<tt:InvalidAfterReboot>false</tt:InvalidAfterReboot>\n" +
                "<tt:Timeout>P1Y</tt:Timeout>\n" +
                "</trt:MediaUri>\n" +
                "<trt:MediaUri>\n" +
                "<tt:Uri>" + rtspUrl + "</tt:Uri>\n" +
                "<tt:InvalidAfterConnect>false</tt:InvalidAfterConnect>\n" +
                "<tt:InvalidAfterReboot>false</tt:InvalidAfterReboot>\n" +
                "<tt:Timeout>P1Y</tt:Timeout>\n" +
                "</trt:MediaUri>\n" +
                "</trt:GetStreamUriResponse>\n" +
                "</env:Body>\n" +
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
                "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\" xmlns:tt=\"http://www.onvif.org/ver10/schema\">\n" +
                "<SOAP-ENV:Body>\n" +
                "<tds:GetSystemDateAndTimeResponse>\n" +
                "<tds:SystemDateAndTime>\n" +
                "<tt:DateTimeType>NTP</tt:DateTimeType>\n" +
                "<tt:DaylightSavings>true</tt:DaylightSavings>\n" +
                "<tt:TimeZone>\n" +
                "<tt:TZ>" + timezone + "</tt:TZ>\n" +
                "</tt:TimeZone>\n" +
                "<tt:UTCDateTime>\n" +
                "<tt:Time>\n" +
                "<tt:Hour>" + hour + "</tt:Hour>\n" +
                "<tt:Minute>" + minute + "</tt:Minute>\n" +
                "<tt:Second>" + second + "</tt:Second>\n" +
                "</tt:Time>\n" +
                "<tt:Date>\n" +
                "<tt:Year>" + year + "</tt:Year>\n" +
                "<tt:Month>" + month + "</tt:Month>\n" +
                "<tt:Day>" + day + "</tt:Day>\n" +
                "</tt:Date>\n" +
                "</tt:UTCDateTime>\n" +
                "<tt:LocalDateTime>\n" +
                "<tt:Time>\n" +
                "<tt:Hour>" + hour + "</tt:Hour>\n" +
                "<tt:Minute>" + minute + "</tt:Minute>\n" +
                "<tt:Second>" + second + "</tt:Second>\n" +
                "</tt:Time>\n" +
                "<tt:Date>\n" +
                "<tt:Year>" + year + "</tt:Year>\n" +
                "<tt:Month>" + month + "</tt:Month>\n" +
                "<tt:Day>" + day + "</tt:Day>\n" +
                "</tt:Date>\n" +
                "</tt:LocalDateTime>\n" +
                "</tds:SystemDateAndTime>\n" +
                "</tds:GetSystemDateAndTimeResponse>\n" +
                "</SOAP-ENV:Body>\n" +
                "</SOAP-ENV:Envelope>";
        return response;
    }

    /**
     * construct the GetCapabilities response
     *
     * @param serverIpAddress eg. 192.168.100.110
     * @return the GetCapabilitiesResponse
     */
    private String constructOnvifGetCapabilitiesResponse(String serverIpAddress) {
        Log.d(TAG, "respond the GetCapabilities request with server ip address of " + serverIpAddress);

        String response = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\" xmlns:tt=\"http://www.onvif.org/ver10/schema\">\n" +
                "<SOAP-ENV:Body>\n" +
                "<tds:GetCapabilitiesResponse>\n" +
                "<tds:Capabilities>\n" +
                "<tt:Device>\n" +
                "<tt:XAddr>http://" + serverIpAddress + ":8080/onvif/services</tt:XAddr>\n" +
                "<tt:Network>\n" +
                "<tt:IPFilter>true</tt:IPFilter>\n" +
                "<tt:ZeroConfiguration>true</tt:ZeroConfiguration>\n" +
                "<tt:IPVersion6>false</tt:IPVersion6>\n" +
                "<tt:DynDNS>true</tt:DynDNS>\n" +
                "</tt:Network>\n" +
                "<tt:System>\n" +
                "<tt:DiscoveryResolve>true</tt:DiscoveryResolve>\n" +
                "<tt:DiscoveryBye>true</tt:DiscoveryBye>\n" +
                "<tt:RemoteDiscovery>false</tt:RemoteDiscovery>\n" +
                "<tt:SystemBackup>false</tt:SystemBackup>\n" +
                "<tt:SystemLogging>true</tt:SystemLogging>\n" +
                "<tt:FirmwareUpgrade>false</tt:FirmwareUpgrade>\n" +
                "<tt:SupportedVersions>\n" +
                "<tt:Major>1</tt:Major>\n" +
                "<tt:Minor>0</tt:Minor>\n" +
                "</tt:SupportedVersions>\n" +
                "</tt:System>\n" +
                "<tt:IO>\n" +
                "<tt:InputConnectors>1</tt:InputConnectors>\n" +
                "<tt:RelayOutputs>0</tt:RelayOutputs>\n" +
                "</tt:IO>\n" +
                "<tt:Security>\n" +
                "<tt:TLS1.1>false</tt:TLS1.1>\n" +
                "<tt:TLS1.2>false</tt:TLS1.2>\n" +
                "<tt:OnboardKeyGeneration>false</tt:OnboardKeyGeneration>\n" +
                "<tt:AccessPolicyConfig>false</tt:AccessPolicyConfig>\n" +
                "<tt:X.509Token>false</tt:X.509Token>\n" +
                "<tt:SAMLToken>false</tt:SAMLToken>\n" +
                "<tt:KerberosToken>false</tt:KerberosToken>\n" +
                "<tt:RELToken>false</tt:RELToken>\n" +
                "</tt:Security>\n" +
                "</tt:Device>\n" +
                "<tt:Events>\n" +
                "<tt:XAddr>http://" + serverIpAddress + ":8080/onvif/services</tt:XAddr>\n" +
                "<tt:WSSubscriptionPolicySupport>false</tt:WSSubscriptionPolicySupport>\n" +
                "<tt:WSPullPointSupport>false</tt:WSPullPointSupport>\n" +
                "<tt:WSPausableSubscriptionManagerInterfaceSupport>false\n" +
                "</tt:WSPausableSubscriptionManagerInterfaceSupport>\n" +
                "</tt:Events>\n" +
                "<tt:Media>\n" +
                "<tt:XAddr>http://" + serverIpAddress + ":8080/onvif/services</tt:XAddr>\n" +
                "<tt:StreamingCapabilities>\n" +
                "<tt:RTPMulticast>false</tt:RTPMulticast>\n" +
                "<tt:RTP_TCP>true</tt:RTP_TCP>\n" +
                "<tt:RTP_RTSP_TCP>true</tt:RTP_RTSP_TCP>\n" +
                "</tt:StreamingCapabilities>\n" +
                "</tt:Media>\n" +
                "</tds:Capabilities>\n" +
                "</tds:GetCapabilitiesResponse>\n" +
                "</SOAP-ENV:Body>\n" +
                "</SOAP-ENV:Envelope>";

        return response;
    }

    /**
     * construct the SetSystemDateAndTime response
     * and we need to parse out the date, hour, seconds, year, month, day that
     * the client provided.
     *
     * @return the SetSystemDateAndTime response
     */
    private String constructOnvifSetSystemDateAndTimeResponse(boolean setupResult) {
        Log.d(TAG, "handle the response of SetSystemDateAndTime request with setup success ? " + setupResult);
        String response;
        if (setupResult) {
            // setup success
            response = "<?xml version='1.0' encoding='utf-8'?>\n" +
                    "<soapenv:Envelope xmlns:soapenv=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\">\n" +
                    "<soapenv:Body>\n" +
                    "<tds:SetSystemDateAndTimeResponse />\n" +
                    "</soapenv:Body>\n" +
                    "</soapenv:Envelope>";
        } else {
            // setup failed
            // we do not support this SetSystemDateAndTime action
            response = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\">\n" +
                    "<SOAP-ENV:Header>\n" +
                    "<wsa:To SOAP-ENV:mustUnderstand=\"true\">\n" +
                    "http://schemas.xmlsoap.org/ws/2004/08/addressing/role/anonymous\n" +
                    "</wsa:To>\n" +
                    "<wsa:Action SOAP-ENV:mustUnderstand=\"true\">\n" +
                    "http://schemas.xmlsoap.org/ws/2004/08/addressing/fault\n" +
                    "</wsa:Action>\n" +
                    "</SOAP-ENV:Header>\n" +
                    "<SOAP-ENV:Body>\n" +
                    "<SOAP-ENV:Fault SOAP-ENV:encodingStyle=\"http://www.w3.org/2003/05/soap-encoding\">\n" +
                    "<SOAP-ENV:Code>\n" +
                    "<SOAP-ENV:Value>SOAP-ENV:Sender</SOAP-ENV:Value>\n" +
                    "<SOAP-ENV:Subcode>\n" +
                    "<SOAP-ENV:Value>wsa:ActionNotSupported</SOAP-ENV:Value>\n" +
                    "</SOAP-ENV:Subcode>\n" +
                    "</SOAP-ENV:Code>\n" +
                    "<SOAP-ENV:Reason>\n" +
                    "<SOAP-ENV:Text xml:lang=\"en\">The [action] cannot be processed at the receiver.\n" +
                    "</SOAP-ENV:Text>\n" +
                    "</SOAP-ENV:Reason>\n" +
                    "<SOAP-ENV:Detail>http://www.onvif.org/ver10/device/wsdl/GetServices</SOAP-ENV:Detail>\n" +
                    "</SOAP-ENV:Fault>\n" +
                    "</SOAP-ENV:Body>\n" +
                    "</SOAP-ENV:Envelope>";
        }

        return response;
    }

    private String constructOnvifGetVideoEncoderConfigurationOptionsResponse(int width, int height) {
        Log.d(TAG, "construct response of GetVideoEncoderConfigurationOptionsResponse request with" +
                "width of " + width + ", and height of " + height);

        String response = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:trt=\"http://www.onvif.org/ver10/media/wsdl\" xmlns:tt=\"http://www.onvif.org/ver10/schema\">\n" +
                "<SOAP-ENV:Body>\n" +
                "<trt:GetVideoEncoderConfigurationOptionsResponse>\n" +
                "<trt:Options>\n" +
                "<tt:QualityRange>\n" +
                "<tt:Min>1</tt:Min>\n" +
                "<tt:Max>10</tt:Max>\n" +
                "</tt:QualityRange>\n" +
                "<tt:JPEG>\n" +
                "<tt:ResolutionsAvailable>\n" +
                "<tt:Width>" + width + "</tt:Width>\n" +
                "<tt:Height>" + height + "</tt:Height>\n" +
                "</tt:ResolutionsAvailable>\n" +
                "<tt:FrameRateRange>\n" +
                "<tt:Min>10</tt:Min>\n" +
                "<tt:Max>15</tt:Max>\n" +
                "</tt:FrameRateRange>\n" +
                "<tt:EncodingIntervalRange>\n" +
                "<tt:Min>1</tt:Min>\n" +
                "<tt:Max>1</tt:Max>\n" +
                "</tt:EncodingIntervalRange>\n" +
                "</tt:JPEG>\n" +
                "<tt:Extension>\n" +
                "<tt:JPEG>\n" +
                "<tt:BitrateRange>\n" +
                "<tt:Min>134000</tt:Min>\n" +
                "<tt:Max>135000</tt:Max>\n" +
                "</tt:BitrateRange>\n" +
                "</tt:JPEG>\n" +
                "</tt:Extension>\n" +
                "</trt:Options>\n" +
                "</trt:GetVideoEncoderConfigurationOptionsResponse>\n" +
                "</SOAP-ENV:Body>\n" +
                "</SOAP-ENV:Envelope>";

        return response;
    }

    private String constructOnvifCreateOSDResponse(String newlyCreatedOSDToken) {
        Log.d(TAG, "construct the response of CreateOSD request with newly created OSDToken " + newlyCreatedOSDToken);
        String response = "<?xml version='1.0' encoding='utf-8'?>\n" +
                "<soapenv:Envelope xmlns:soapenv=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\">\n" +
                "<soapenv:Body>\n" +
                "<tds:CreateOSDResponse>\n" +
                "<tds:OSDToken>" + newlyCreatedOSDToken + "</tds:OSDToken>\n" +
                "</tds:CreateOSDResponse>\n" +
                "</soapenv:Body>\n" +
                "</soapenv:Envelope>";

        return response;
    }

    /**
     * @return the response of SetOSD request
     */
    private String constructOnvifSetOSDResponse() {
        Log.d(TAG, "construct the response of SetOSD request with ");

        String response = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<soapenv:Envelope xmlns:soapenv=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\" xmlns:tt=\"http://www.onvif.org/ver10/schema\">\n" +
                "<soapenv:Body>\n" +
                "<tds:SetOSDResponse />\n" +
                "</soapenv:Body>\n" +
                "</soapenv:Envelope>";

        return response;
    }


}
