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
import android.telephony.TelephonyManager;
import android.util.Base64;
import android.util.Log;

import net.majorkernelpanic.onvif.DeviceStaticInfo;

import java.net.DatagramPacket;
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
        } else if ((ipAddress = Utilities.getLocalIpAddress(true)) != null) {
            return ipAddress;
        }

        throw new RuntimeException("fail to get local device ip address");
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


    /**
     * @param urnUUID      例如:e32e6863-ea5e-4ee4-997e-69539d1ff2cc
     * @param requestUUID  例如:0a6dc791-2be6-4991-9af1-454778a1917a
     * @param localAddress
     * @return
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
}