package net.majorkernelpanic.onvif;

import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import net.majorkernelpanic.spydroid.SpydroidApplication;
import net.majorkernelpanic.spydroid.Utilities;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 在SpyDroid的基础上实现ONVIF协议
 * <p>
 * TODO: 最好是将{@link SimpleONVIFManager}实现为一个{@link android.app.Service},并保证其运行在一个单独的进程当中.
 */
public class SimpleONVIFManager {
    private static final String TAG = "simpleOnVifManager";

    private static Handler sProbePacketPoster;

    static {
        HandlerThread workerThread = new HandlerThread("multicast_thread");
        workerThread.start();
        sProbePacketPoster = new Handler(workerThread.getLooper());
    }

    private Context context;
    private String serverIp;

    /**
     * ONVIF协议当中使用的组播的端口号
     */
    private static final int MULTICAST_PORT = 3702;
    /**
     * ONVIF协议当中使用的组播地址是固定的,即"239.255.255.250".
     * 是ONVIF协议的内部约定.
     * <p>
     * 但是在具体部署到Android设备上的时候，会发现不能直接将一个IPV4格式的地址用作组播地址.
     * 如果一个设备当中的/proc/net目录下只有igmp6设备的话，那么我们需要使用IPV6的组播地址，才可以
     * 在设备之间组播状态信息
     * 如果设备的/proc/net目录下同时具有igmp和igmp6设备的话，那么使用IPV4的组播地址和IPV6的组播
     * 地址都可以进行组播状态
     * 我们可以简单的理解为IPV6地址对IPV4进行了兼容.
     * 例如我们目前使用的华为设备，再其/proc/net目录下就只有igmp6设备，因此他只能使用IPV6格式的地址.
     * 因此我们需要将"239.255.255.250"转换成对应的IPV6格式.
     * 当然如果我们可以确保我们的设备上只有igmp时，我们就需要将地址设定为"239.255.255.250".(视具体的
     * 运行情况来决定).
     * <p>
     * 以下的地址是"239.255.255.250"对应的IPV6的格式.
     */
    private static final String MULTICAST_HOST_IP = "FF02::1";
//    private static final String MULTICAST_HOST_IP = "0:0:0:0:0:ffff:efff:fffa";
//    private static final String MULTICAST_HOST_IP = "239.255.255.250";

    private static final ExecutorService PROBE_PACKET_RECEIVE_EXECUTOR = Executors.newSingleThreadExecutor();

    private static final int PACKET_SEND_TIMEOUT = 10000; // 10 seconds

    private static final int PROBE_PACKET_SEND_OUT_INTERVAL = 500; // 500ms

    private WifiManager.MulticastLock multicastLock;
    private MulticastSocket multicastSocket;

    private static final boolean DEBUG_SEND_PACKET = false;

    public SimpleONVIFManager(Context context) {
        this.context = context;

        WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            Log.d(TAG, "we have acquired the wifi mulitcast lock");
            multicastLock = wifiManager.createMulticastLock("onvif_data_broadcast");
            multicastLock.acquire();
        }
        multicastSocket = createMulticastSocket();

        serverIp = getLocalDevIp();
        initData();

        if (DEBUG_SEND_PACKET) {
            startSendProbePacket();
        }

        receiveProbePacket();
    }

    /**
     * 对于IPCamera来说，不需要发送packet,只需要接收来自IPCameraViewer发送的探测packet.
     * 这里只是为了测试，稍后进行移除.
     */
    private void startSendProbePacket() {
        sProbePacketPoster.post(new Runnable() {
            @Override
            public void run() {
                byte[] data = {};
                sendProbePacket(data);
                sProbePacketPoster.postDelayed(this, PROBE_PACKET_SEND_OUT_INTERVAL);
            }
        });
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

    private MulticastSocket createMulticastSocket() {
        try {
            if (multicastLock.isHeld()) {
                Log.d(TAG, "we have held the WifiMulticastLock");
            } else {
                Log.d(TAG, "fail to held the WifiMulticastLock, then user may fail to receive the Multicast message");
            }
            InetAddress groupAddress = InetAddress.getByName(MULTICAST_HOST_IP);
            MulticastSocket multicastSocket = new MulticastSocket(MULTICAST_PORT);
            multicastSocket.setTimeToLive(10);
            multicastSocket.setSoTimeout(PACKET_SEND_TIMEOUT);
            multicastSocket.setNetworkInterface(NetworkInterface.getByName("wlan0"));
            multicastSocket.joinGroup(groupAddress);
            return multicastSocket;
        } catch (UnknownHostException e) {
            Log.e(TAG, "the given host of " + MULTICAST_HOST_IP + " are unknown", e);
        } catch (IOException e) {
            Log.e(TAG, "fail to create the mulitcast socket of " + MULTICAST_PORT, e);
        }
        return null;
    }

    private void initData() {
        Log.d(TAG, "init the ONVIF data");
        SpydroidApplication application = (SpydroidApplication) ((Activity) context).getApplication();
        DeviceBackBean deviceBackBean = application.getDeviceBackBean();
        deviceBackBean.setIpAddress(serverIp);
        deviceBackBean.setUserName(DeviceStaticInfo.USER_NAME);
        deviceBackBean.setPsw(DeviceStaticInfo.USER_PSW);
        deviceBackBean.setServiceUrl("http://" + serverIp + ":8080/onvif/device_service");
        application.setDeviceBackBean(deviceBackBean);
    }

    private void sendProbePacket(byte[] data) {
        try {
            InetAddress groupAddress = InetAddress.getByName(MULTICAST_HOST_IP);
            DatagramPacket packet = new DatagramPacket(data,
                    data.length,
                    groupAddress,
                    MULTICAST_PORT);
            if (multicastSocket != null) {
                multicastSocket.send(packet);
            }
        } catch (UnknownHostException e) {
            Log.e(TAG, "fail to get the group address of " + MULTICAST_HOST_IP, e);
        } catch (IOException e) {
            Log.e(TAG, "fail send packet out", e);
        }
    }

    private ReentrantLock probePacketHandleLock = new ReentrantLock();

    /**
     * 接收来自IPCameraViewer的探测packet
     */
    private void receiveProbePacket() {
        Log.d(TAG, "start receive the Probe packet");
        if (multicastSocket == null) {
            Log.e(TAG, "the multicast socket are null");
            return;
        }
        PROBE_PACKET_RECEIVE_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "start receive probe packet");
                    InetAddress groupAddress = InetAddress.getByName(MULTICAST_HOST_IP);

                    // 我们可能同时会收到来自于很多设备的packet
                    final byte[] receiveDataBuffer = new byte[1024 * 4];
                    for (; ; ) {
                        probePacketHandleLock.lock();
                        DatagramPacket packet = new DatagramPacket(
                                receiveDataBuffer,
                                receiveDataBuffer.length,
                                groupAddress,
                                MULTICAST_PORT);
                        multicastSocket.receive(packet);
                        probePacketHandleLock.unlock();

                        Log.e(TAG, "we have received the probe packet");
                        handleReceivedProbePacket(packet);
                    }
                } catch (UnknownHostException e) {
                    Log.e(TAG, "Exception happened while get the InetAddress of " + MULTICAST_HOST_IP, e);
                } catch (IOException e) {
                    Log.e(TAG, "Exception happened while receive the packet", e);
                    PROBE_PACKET_RECEIVE_EXECUTOR.execute(this);
                }
            }
        });
    }

    /**
     * 处理我们接收到的探测packet
     */
    private void handleReceivedProbePacket(DatagramPacket probePacket) {
        Log.d(TAG, "------------> the raw packet we received are " + Utilities.flattenDatagramPacket(probePacket));
        final int packetDataOffset = probePacket.getOffset();
        final int packetDataLen = probePacket.getLength();
        byte[] receivedData = new byte[packetDataLen];
        System.arraycopy(probePacket.getData(), packetDataOffset, receivedData, 0, packetDataLen);

        String receivedRawMsg = new String(receivedData, packetDataOffset, packetDataLen);
        Log.d(TAG, "the raw received message are " + receivedRawMsg);

        // 我们此时接收到了由IPCameraViewer发送过来的探测数据之后，需要返回响应包给对应的IPCameraViewer
        ONVIFPacketHandler handler = new ONVIFPacketHandler(receivedRawMsg);
        ONVIFDevDiscoveryReqHeader reqHeader = handler.getReqHeader();
        String reqMessageId = reqHeader.getMessageId();
        String reqAction = reqHeader.getAction();

        Log.d(TAG, String.format("req message Id are %s, req action are %s", reqMessageId, reqAction));

        // TODO: 返回响应的probeMatch packet
        // TODO: 以下的数据封装有问题
//        String requestUUID = Utilities.getDevId(context);
//        String sendBack = Utilities.generateDeviceProbeMatchPacket(reqMessageId, requestUUID, serverIp);
//        byte[] sendBuf = sendBack.getBytes();
//
//        try {
//            Log.d(TAG, String.format("the probe packet address are %:%s" + probePacket.getAddress(), probePacket.getPort()));
//            DatagramPacket packet = new DatagramPacket(sendBuf, sendBuf.length, probePacket.getAddress(), probePacket.getPort());
//            multicastSocket.send(packet);
//        } catch (final Exception e) {
//            Log.e(TAG, "Exception happened while we send the response packet", e);
//        }
//        Log.d(TAG, "send the response data back");
    }

}
