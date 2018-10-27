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
import android.telephony.TelephonyManager;
import android.util.Base64;
import android.util.Log;

import net.majorkernelpanic.onvif.DeviceStaticInfo;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
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


    /**
     * Returns the IP address of the first configured interface of the device
     *
     * @param removeIPv6 If true, IPv6 addresses are ignored
     * @return the IP address of the first configured interface or null
     */
    public static String getLocalIpAddress(boolean removeIPv6) {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (inetAddress.isSiteLocalAddress() &&
                            !inetAddress.isAnyLocalAddress() &&
                            (!removeIPv6 || isIpv4Address(inetAddress.getHostAddress()))) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            Log.e(TAG, "Socket exception happened", e);
        }
        return null;
    }

    public static String generateDeviceProbeMatchRequest(String urnUUID, String requestUUID, String localAddress) {
        StringBuilder sb;
        sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\r\n")
                .append("<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:SOAP-ENC=\"http://www.w3.org/2003/05/soap-encoding\" xmlns:xsi=\"http://www" + ".w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" " + "xmlns:wsdd=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\" xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\" xmlns:dn=\"http://www.onvif" + ".org/ver10/network/wsdl\">\r\n")
                .append("   <SOAP-ENV:Header>\r\n").append(" <wsa:MessageID>urn:uuid").append(urnUUID).append("</wsa:MessageID>\r\n")
                .append("       <wsa:RelatesTo>").append(requestUUID).append("</wsa:RelatesTo>\r\n")
                .append("       <wsa:ReplyTo SOAP-ENV:mustUnderstand=\"true\">\r\n")
                .append("           <wsa:Address>http://schemas.xmlsoap.org/ws/2004/08/addressing/role/anonymous</wsa:Address>\r\n")
                .append("       </wsa:ReplyTo>\r\n")
                .append("       <wsa:To SOAP-ENV:mustUnderstand=\"true\">http://schemas.xmlsoap.org/ws/2004/08/addressing/role/anonymous</wsa:To>\r\n")
                .append(" <wsa:Action SOAP-ENV:mustUnderstand=\"true\">http://schemas.xmlsoap.org/ws/2005/04/discovery/ProbeMatches</wsa:Action>\r\n")
                .append(" <wsdd:AppSequence InstanceId=\"0\" MessageNumber=\"5\"></wsdd:AppSequence>\r\n")
                .append("   </SOAP-ENV:Header>\r\n")
                .append("   <SOAP-ENV:Body>\r\n")
                .append("       <wsdd:ProbeMatches>\r\n")
                .append("           <wsdd:ProbeMatch>\r\n")
                .append("               <wsa:EndpointReference>\r\n")
                .append("                   <wsa:Address>urn:uuid:").append(urnUUID).append("</wsa:Address>\r\n")
                .append("               </wsa:EndpointReference>\r\n")
                .append("               <wsdd:Types>dn:NetworkVideoTransmitter</wsdd:Types>\r\n")
                .append("                   <wsdd:Scopes>onvif://www.onvif.org/Profile/Streaming onvif://www.onvif.org/type/video_encoder onvif://www.onvif" + ".org/type/audio_encoder onvif://www.onvif.org/hardware/ONVIF-Emu onvif://www.onvif.org/name/ONVIF-Emu onvif://www.onvif.org/location/Default</wsdd:Scopes> \r\n")
                .append("               <wsdd:XAddrs>http://").append(localAddress).append(":8080/onvif/device_service</wsdd:XAddrs>\r\n")
                .append("       <wsdd:MetadataVersion>10</wsdd:MetadataVersion>\r\n")
                .append("       </wsdd:ProbeMatch>\r\n")
                .append("       </wsdd:ProbeMatches>\r\n")
                .append("    </SOAP-ENV:Body>\r\n")
                .append("</SOAP-ENV:Envelope>");
        return sb.toString();
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

    private static String createAuthString() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.CHINA);
        String created = df.format(new Date());
        String nonce = getNonce();
        return getEncodedPsw(nonce, DeviceStaticInfo.USER_PSW, created);
    }
}