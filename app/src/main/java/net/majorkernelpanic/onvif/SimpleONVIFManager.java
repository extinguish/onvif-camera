package net.majorkernelpanic.onvif;

import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import net.majorkernelpanic.spydroid.SpydroidApplication;
import net.majorkernelpanic.spydroid.Utilities;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 在SpyDroid的基础上实现ONVIF协议
 */
public class SimpleONVIFManager {
    private static final String TAG = "simpleOnVifManager";
    private Context context;
    private String serverIp;
    private static final ExecutorService UDP_BROADCAST_WORKER = Executors.newSingleThreadExecutor();

    public SimpleONVIFManager(Context context) {
        this.context = context;
        serverIp = getLocalDevIp();
        initData();
        UDP_BROADCAST_WORKER.execute(new UdpBroadcastTask());
    }

    private String getLocalDevIp() {
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

    private void initData() {
        Log.d(TAG, "init the ONVIF data");
        SpydroidApplication application = (SpydroidApplication) ((Activity) context).getApplication();
        DeviceBackBean deviceBackBean = application.getDeviceBackBean();
        deviceBackBean.setIpAddress(serverIp);
        deviceBackBean.setUserName(DeviceStaticInfo.USRE_NAME);
        deviceBackBean.setPsw(DeviceStaticInfo.USER_PSW);
        deviceBackBean.setServiceUrl("http://" + serverIp + ":8080/onvif/device_service");
        application.setDeviceBackBean(deviceBackBean);
    }

    /**
     * ONVIF协议当中使用的组播的端口号
     */
    private static final int MULTICAST_PORT = 3702;
    /**
     * ONVIF协议当中使用的组播地址是固定的.
     * 是ONVIF协议的内部约定
     */
    private static final String MULTICAST_HOST_IP = "239.255.255.250";

    /**
     * 用于进行组播任务
     * <p>
     * ONVIF协议当中，IPCamera实现ws-discovery是通过组播来实现的.
     */
    class UdpBroadcastTask implements Runnable {

        @Override
        public void run() {
            Log.d(TAG, "start to broadcast the UDP packet");
            MulticastSocket socket = null;
            InetAddress address = null;
            try {
                socket = new MulticastSocket(MULTICAST_PORT);
                address = InetAddress.getByName(MULTICAST_HOST_IP);

                socket.joinGroup(address);
                DatagramPacket packet;

                final byte[] receiveBuf = new byte[1024];
                String receivedMsg;

                for (; ; ) {
                    // 发送组播包
                    packet = new DatagramPacket(receiveBuf, receiveBuf.length,
                            address, MULTICAST_PORT);
                    socket.receive(packet);
                    receivedMsg = new String(receiveBuf, packet.getOffset(), packet.getLength());
                    Log.d(TAG, "the received message are " + receivedMsg);

                    // 接听组播包
                    ONVIFPacketHandler handler = new ONVIFPacketHandler(receivedMsg);
                    ONVIFDevDiscoveryReqHeader reqHeader = handler.getReqHeader();
                    String reqMessageId = reqHeader.getMessageId();
                    String reqAction = reqHeader.getAction();

                    Log.d(TAG, String.format("req message Id are %s, req action are %s", reqMessageId, reqAction));

                    // 返回响应的probeMatch packet
                    String requestUUID = Utilities.getDevId(context);
                    String sendBack = Utilities.generateDeviceProbeMatchRequest(reqMessageId, requestUUID, serverIp);
                    byte[] sendBuf = sendBack.getBytes();
                    packet = new DatagramPacket(sendBuf, sendBuf.length, packet.getAddress(), MULTICAST_PORT);
                    socket.send(packet);
                }
            } catch (IOException e) {
                Log.e(TAG, "Exception happened while send the Multicast packet", e);
            }

            try {
                if (socket == null) {
                    return;
                }
                socket.leaveGroup(address);
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "fail to leave the group", e);
            }
        }
    }
}
