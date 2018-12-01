package net.majorkernelpanic.spydroid;

/**
 * Licensed under the GNU LESSER GENERAL PUBLIC LICENSE, version 2.1, dated February 1999.
 * <p>
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the latest version of the GNU Lesser General
 * Public License as published by the Free Software Foundation;
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program (LICENSE.txt); if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.MemoryFile;
import android.telephony.TelephonyManager;
import android.util.Base64;
import android.util.Log;

import net.majorkernelpanic.onvif.DeviceStaticInfo;

import org.apache.http.NameValuePair;

import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * This class provides a variety of basic utility methods that are not
 * dependent on any other classes within the org.jamwiki package structure.
 */
public class Utilities {
    private static final String TAG = "utilities";

    private static Pattern VALID_IPV4_PATTERN = null;
    private static Pattern VALID_IPV6_PATTERN = null;
    private static final String ipv4Pattern = "(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])";
    private static final String ipv6Pattern = "([0-9a-f]{1,4}:){7}([0-9a-f]){1,4}";

    static {
        try {
            VALID_IPV4_PATTERN = Pattern.compile(ipv4Pattern, Pattern.CASE_INSENSITIVE);
            VALID_IPV6_PATTERN = Pattern.compile(ipv6Pattern, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException e) {
            //logger.severe("Unable to compile pattern", e);
        }
    }

    /**
     * Determine if the given string is a valid IPv4 or IPv6 address.  This method
     * uses pattern matching to see if the given string could be a valid IP address.
     *
     * @param ipAddress A string that is to be examined to verify whether or not
     *                  it could be a valid IP address.
     * @return <code>true</code> if the string is a value that is a valid IP address,
     * <code>false</code> otherwise.
     */
    public static boolean isIpAddress(String ipAddress) {
        Matcher m1 = Utilities.VALID_IPV4_PATTERN.matcher(ipAddress);
        if (m1.matches()) {
            return true;
        }
        Matcher m2 = Utilities.VALID_IPV6_PATTERN.matcher(ipAddress);
        return m2.matches();
    }

    public static boolean isIpv4Address(String ipAddress) {
        Matcher m1 = Utilities.VALID_IPV4_PATTERN.matcher(ipAddress);
        return m1.matches();
    }

    public static boolean isIpv6Address(String ipAddress) {
        Matcher m1 = Utilities.VALID_IPV6_PATTERN.matcher(ipAddress);
        return m1.matches();
    }

    public static String getLocalDevIp(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            throw new RuntimeException("Fail to get the WifiManager");
        }
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        String ipAddress;
        if (wifiInfo != null && wifiInfo.getNetworkId() > -1) {
            int rawIpAddress = wifiInfo.getIpAddress();
            ipAddress = String.format(Locale.ENGLISH, "%d.%d.%d.%d", rawIpAddress & 0xff,
                    (rawIpAddress >> 8) & 0xff, (rawIpAddress >> 16) & 0xff,
                    (rawIpAddress >> 24) & 0xff);
            Log.d(TAG, "the ip address of the ONVIF-IPCamera are " + ipAddress);
            return ipAddress;
        } else if ((ipAddress = Utilities.getLocalIpAddress()) != null) {
            return ipAddress;
        } else {
            Log.e(TAG, "fail to get local device ip address");
            return null;
        }
    }


    /**
     * Returns the IP address of the first configured interface of the device
     *
     * @return the IP address of the first configured interface or null
     */
    public static String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (final Exception e) {
            Log.e(TAG, "Exception happened while get the device ip address", e);
        }
        return null;
    }

    /**
     * 针对ONVIF test tool的返回的packet的内容
     *
     * @param urnUUID         当前设备的唯一标识ID
     * @param reqMsgId        由ONVIF test tool发送的probePacket当中messageId
     * @param serverIpAddress 当前设备的IP地址
     * @return probe match packet
     */
    public static String generateDeviceProbeMatchPacket1(String urnUUID, String reqMsgId, String serverIpAddress) {
        StringBuffer sb;
        sb = new StringBuffer();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\r\n");
        sb.append("<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:SOAP-ENC=\"http://www.w3.org/2003/05/soap-encoding\" xmlns:xsi=\"http://www" + ".w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" " + "xmlns:wsdd=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\" xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\" xmlns:dn=\"http://www.onvif" + ".org/ver10/network/wsdl\">\r\n");
        sb.append(" <SOAP-ENV:Header>\r\n");
        sb.append(" <wsa:MessageID>urn:uuid" + urnUUID + "</wsa:MessageID>\r\n");
        sb.append(" <wsa:RelatesTo>" + reqMsgId + "</wsa:RelatesTo>\r\n");
        sb.append(" <wsa:ReplyTo SOAP-ENV:mustUnderstand=\"true\">\r\n");
        sb.append(" <wsa:Address>http://schemas.xmlsoap.org/ws/2004/08/addressing/role/anonymous</wsa:Address>\r\n");
        sb.append(" </wsa:ReplyTo>\r\n");
        sb.append(" <wsa:To SOAP-ENV:mustUnderstand=\"true\">http://schemas.xmlsoap.org/ws/2004/08/addressing/role/anonymous</wsa:To>\r\n");
        sb.append(" <wsa:Action SOAP-ENV:mustUnderstand=\"true\">http://schemas.xmlsoap.org/ws/2005/04/discovery/ProbeMatches</wsa:Action>\r\n");
        sb.append(" <wsdd:AppSequence InstanceId=\"0\" MessageNumber=\"5\"></wsdd:AppSequence>\r\n");
        sb.append(" </SOAP-ENV:Header>\r\n");
        sb.append(" <SOAP-ENV:Body>\r\n");
        sb.append(" <wsdd:ProbeMatches>\r\n");
        sb.append(" <wsdd:ProbeMatch>\r\n");
        sb.append(" <wsa:EndpointReference>\r\n");
        sb.append(" <wsa:Address>urn:uuid:" + urnUUID + "</wsa:Address>\r\n");
        sb.append(" </wsa:EndpointReference>\r\n");
        sb.append(" <wsdd:Types>dn:NetworkVideoTransmitter</wsdd:Types>\r\n");
        sb.append(" <wsdd:Scopes>onvif://www.onvif.org/Profile/Streaming onvif://www.onvif.org/type/video_encoder onvif://www.onvif" + ".org/type/audio_encoder onvif://www.onvif.org/hardware/ONVIF-Emu onvif://www.onvif.org/name/ONVIF-Emu onvif://www.onvif.org/location/Default</wsdd:Scopes> \r\n");
        sb.append(" <wsdd:XAddrs>http://" + serverIpAddress + ":8080/onvif/device_service</wsdd:XAddrs>\r\n");
        sb.append(" <wsdd:MetadataVersion>10</wsdd:MetadataVersion>\r\n");
        sb.append(" </wsdd:ProbeMatch>\r\n");
        sb.append(" </wsdd:ProbeMatches>\r\n");
        sb.append(" </SOAP-ENV:Body>\r\n");
        sb.append("</SOAP-ENV:Envelope>");
        return sb.toString();
    }

    /**
     * 作用同{@link #generateDeviceProbeMatchPacket(String, String, String)}一样，
     * 但是对应的数据格式不一样.
     * <p>
     * ONVIF协议的内部的soap协议支持有差别.
     */
    public static String generateDeviceProbeMatchPacket(String urnUUID, String requestUUID, String localAddress) {
        return "<s:Envelope xmlns:a=\"http://www.w3.org/2005/08/addressing\"\n" +
                "    xmlns:d=\"http://docs.oasis-open.org/ws-dd/ns/discovery/2009/01\"\n" +
                "    xmlns:i=\"http://printer.example.org/2003/imaging\"\n" +
                "    xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\">\n" +
                "    <s:Header>\n" +
                "        <a:Action>http://docs.oasis-open.org/ws-dd/ns/discovery/2009/01/ProbeMatches</a:Action>\n" +
                "        <a:MessageID>urn:uuid:" + urnUUID + "</a:MessageID>\n" +
                "        <a:RelatesTo>urn:uuid:" + requestUUID +
                "        </a:RelatesTo>\n" +
                "        <a:To>http://www.w3.org/2005/08/addressing/anonymous\n" +
                "        </a:To>\n" +
                "        <d:AppSequence" +
                "            InstanceId=\"1077004800\"\n" +
                "            MessageNumber=\"2\" />\n" +
                "    </s:Header>\n" +
                "    <s:Body>\n" +
                "        <d:ProbeMatches>\n" +
                "            <d:ProbeMatch>\n" +
                "                <a:EndpointReference>" +
                "                    <a:Address>urn:uuid:98190dc2-0890-4ef8-ac9a-5940995e6119</a:Address>\n" +
                "                </a:EndpointReference>\n" +
                "                <d:Types>i:PrintBasic i:PrintAdvanced</d:Types>\n" +
                "                <d:Scopes>\n" +
                "                    ldap:///ou=engineering,o=examplecom,c=us\n" +
                "                    ldap:///ou=floor1,ou=b42,ou=anytown,o=examplecom,c=us\n" +
                "                    http://itdept/imaging/deployment/2004-12-04\n" +
                "                </d:Scopes>\n" +
                "                <d:XAddrs>http://" + localAddress + ":8080/onvif/device_service</d:XAddrs>\n" +
                "                <d:MetadataVersion>75965</d:MetadataVersion>\n" +
                "            </d:ProbeMatch>\n" +
                "        </d:ProbeMatches>\n" +
                "    </s:Body>\n" +
                "</s:Envelope>";
    }

    @SuppressLint("HardwareIds")
    public static String getDevId(Context context) {
        TelephonyManager telephonyManager = (TelephonyManager) context.getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager == null) {
            throw new RuntimeException("fail to get the system telephony service");
        }
        return telephonyManager.getDeviceId();
    }

    /**
     * @param messageId       hello消息的id, 例如0f5d604c-81ac-4abc-8010-51dbffad55f2
     * @param appSequenceId   hello消息的序列id, 例如369a7d7b-5f87-48a4-aa9a-189edf2a8772
     * @param endPointAddress IPCamera的设备标识, 例如37f86d35-e6ac-4241-964f-1d9ae46fb366
     * @return hello消息
     */
    public static String generateHelloPacket(String messageId, String appSequenceId, String endPointAddress) {
        String helloPacket = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                "<soap:Envelope\n" +
                "    xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\"\n" +
                "    xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\"\n" +
                "    xmlns:wsd=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\"\n" +
                "    xmlns:wsdp=\"http://schemas.xmlsoap.org/ws/2006/02/devprof\">\n" +
                "<soap:Header>\n" +
                "    <wsa:To>\n" +
                "        urn:schemas-xmlsoap-org:ws:2005:04:discovery\n" +
                "    </wsa:To>\n" +
                "    <wsa:Action>\n" +
                "        http://schemas.xmlsoap.org/ws/2005/04/discovery/Hello\n" +
                "    </wsa:Action>\n" +
                "    <wsa:MessageID>\n" +
                "        urn:uuid:" + messageId + "\n" +
                "    </wsa:MessageID>\n" +
                "    <wsd:AppSequence InstanceId=\"2\"\n" +
                "        SequenceId=\"urn:uuid:" + appSequenceId + "\"\n" +
                "        MessageNumber=\"14\">\n" +
                "    </wsd:AppSequence>\n" +
                "</soap:Header>\n" +
                "<soap:Body>\n" +
                "    <wsd:Hello>\n" +
                "        <wsa:EndpointReference>\n" +
                "            <wsa:Address>\n" +
                "                urn:uuid:" + endPointAddress + "\n" +
                "            </wsa:Address>\n" +
                "        </wsa:EndpointReference>\n" +
                "        <wsd:Types>wsdp:Device</wsd:Types>\n" +
                "        <wsd:MetadataVersion>2</wsd:MetadataVersion>\n" +
                "    </wsd:Hello>\n" +
                "</soap:Body>";
        return helloPacket;
    }

    /**
     * 在开始设置ipcam的包中，需要在包头中加入ipcam的鉴权。官方给的公式是：
     * Digest=B64Encode(SHA1(B64ENCODE(Nonce)+Date+Password))
     * <p>
     * nonce: 一个16位的随机数即可
     * Sha-1: MessageDigest md = MessageDigest.getInstance("SHA-1");
     * date：参考值："2018-10-27T09:13:35Z";  由客户端请求给出
     *
     * @param nonce    16位随机数
     * @param password 用户密码,这个密码由IPC指定
     * @param date     当前日期
     */
    public static String getEncodedPsw(String nonce, String password, String date) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] b1 = Base64.decode(nonce.getBytes(), Base64.DEFAULT);
            byte[] b2 = date.getBytes(); // "2018-10-27T09:13:35Z";
            byte[] b3 = password.getBytes();
            md.update(b1, 0, b1.length);
            md.update(b2, 0, b2.length);
            md.update(b3, 0, b3.length);
            byte[] b4 = md.digest();
            String result = new String(Base64.encode(b4, Base64.DEFAULT));
            return result.replace("\n", "");
        } catch (Exception e) {
            Log.e(TAG, "Exception happened while encode the password", e);
            return "";
        }
    }

    public static String getNonce() {
        String base = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 24; i++) {
            int number = random.nextInt(base.length());
            sb.append(base.charAt(number));
        }
        return sb.toString();
    }

    /**
     * 这里的AuthString是用于IPCamera鉴权使用的
     */
    public static String createAuthString() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.CHINA);
        String created = df.format(new Date());
        String nonce = getNonce();
        return getEncodedPsw(nonce, DeviceStaticInfo.USER_PSW, created);
    }

    public static String flattenDatagramPacket(DatagramPacket packet) {
        return String.format("packet address : %s, packet port : %s, packet socket address %s, packet data length : %s",
                packet.getAddress().toString(),
                packet.getPort(),
                packet.getSocketAddress().toString(),
                packet.getLength());
    }

    /**
     * 以下的实现当中，采用的是API-21当中的MemoryFile.
     * 在API-27的版本当中，MemoryFile已经是简单的SharedMemory的包装了，
     * 之后添加对API-27的兼容适配.
     * <p>
     * 在API-21当中，MemoryFile还是通过直接同Native内存交互来获取到文件内容;
     * 但是在API-27当中，MemoryFile已经修改成通过{@link java.nio.ByteBuffer}来同
     * Direct Memory来进行交互.
     */
    public static MemoryFile getMemoryFile(FileDescriptor descriptor, int length) {
        try {
            MemoryFile memoryFile = new MemoryFile("adas_stream", 1);
            memoryFile.close();

            Field mFDField = memoryFile.getClass().getDeclaredField("mFD");
            mFDField.setAccessible(true);
            mFDField.set(memoryFile, descriptor);

            // 理论上我们可以直接通过MemoryFile(name, fileLength);来设置mLength的值
            // 但是这里通过反射设置MemoryFile的大小主要是考虑到不想在创建MemoryFile初始时，就
            // 创建一个很大的文件，而是在初始时之后创建一个很小的文件，然后在之后再动态的设置大小.
            Field mLengthField = memoryFile.getClass().getDeclaredField("mLength");
            mLengthField.setAccessible(true);
            mLengthField.set(memoryFile, length);

            @SuppressLint("PrivateApi")
            Method mmapMethod = memoryFile.getClass().getDeclaredMethod("native_mmap",
                    FileDescriptor.class, int.class, int.class);
            mmapMethod.setAccessible(true);
            long address = (long) mmapMethod.invoke(memoryFile, descriptor, length, 0x01 | 0x02);
            Field mAddressField = memoryFile.getClass().getDeclaredField("mAddress");
            mAddressField.setAccessible(true);
            mAddressField.set(memoryFile, address);

            return memoryFile;
        } catch (IOException e) {
            Log.e(TAG, "getMemoryFile: IO exception happened ", e);
        } catch (NoSuchFieldException e) {
            Log.d(TAG, "getMemoryFile: Reflection error, do not find field", e);
        } catch (IllegalAccessException e) {
            Log.d(TAG, "getMemoryFile: Fail to access field", e);
        } catch (NoSuchMethodException e) {
            Log.d(TAG, "getMemoryFile: fail to find specified method", e);
        } catch (InvocationTargetException e) {
            Log.d(TAG, "getMemoryFile: fail to invoke target", e);
        }
        return null;
    }

    public static void dumpParams(List<NameValuePair> nameValuePairList) {
        StringBuilder paramContent = new StringBuilder();
        for (NameValuePair pair : nameValuePairList) {
            paramContent.append("name = ").append(pair.getName())
                    .append(", value = ").append(pair.getValue());
        }
        Log.e(TAG, paramContent.toString());
    }


    public static void printByteArr(String tag, byte[] arr) {
        StringBuilder content = new StringBuilder();
        for (byte ele : arr) {
            content.append(ele).append(' ');
        }
        Log.d(tag, content.toString());
    }


}