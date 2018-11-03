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

import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
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

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * 在SpyDroid的基础上实现ONVIF协议
 * <p>
 * 最好是将{@link SimpleONVIFManager}实现为一个{@link android.app.Service},并保证其运行在一个单独的进程当中.
 *
 * 在使用ONVIF Device Test Tool做协议测试时，首先要确保ONVIF Device Test Tool与我们当前的设备可以互相ping通.
 * 然后就是确保组播地址是可行的.
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
    // TODO: 这里的地址是错误的，但是目前我们只能用这个地址测试，因为下面的IPV6版本的地址有问题(需要换成其他的设备测试一下)
//     private static final String MULTICAST_HOST_IP = "FF02::1";
//    private static final String MULTICAST_HOST_IP = "239.255.255.250"; // 这是正式的ws-service要求的组播地址，如果希望我们的IPCamera被发现，必须将我们的组播地址设置为该值
//    private static final String MULTICAST_HOST_IP = "0:0:0:0:0:ffff:efff:fffa";
//    private static final String MULTICAST_HOST_IP = "ff00:0:0:0:0:0:efff:fffa";
    // 比较奇怪，虽然官方规定ONVIF的组播地址是239.255.255.250,但是如果我们真的将我们的组播地址
    // 设定为239.255.255.250的话，反而是无法收到来自"ONVIF Device Test Tool"
    // 的probe消息的
    // 只有将地址设定为239.1.1.234下面这种随意选定的一个组播地址，反而符合了规范(具体原因还需要继续分析)
    private static final String MULTICAST_HOST_IP = "239.1.1.234";

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

        serverIp = Utilities.getLocalDevIp(context);
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
                    // 重新进行尝试
                    PROBE_PACKET_RECEIVE_EXECUTOR.execute(this);
                }
            }
        });
    }

    /**
     * 处理我们接收到的探测packet
     */
    private void handleReceivedProbePacket(DatagramPacket probePacket) {
        Log.d(TAG, "--> the raw packet we received are \n" + Utilities.flattenDatagramPacket(probePacket));
        final int packetDataOffset = probePacket.getOffset();
        final int packetDataLen = probePacket.getLength();
        byte[] receivedData = new byte[packetDataLen];
        System.arraycopy(probePacket.getData(), packetDataOffset, receivedData, 0, packetDataLen);

        String receivedRawMsg = new String(receivedData, packetDataOffset, packetDataLen);
        Log.d(TAG, "the raw received message are " + receivedRawMsg);

        // 我们此时接收到了由IPCameraViewer发送过来的探测数据之后，
        // 然后就是返回响应包给对应的IPCameraViewer
        // 首先解析我们接收到的数据
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
            saxParser.parse(new ByteArrayInputStream(receivedData), handler);
        } catch (SAXException e) {
            Log.e(TAG, "SAXException happened while processing the received message", e);
        } catch (IOException e) {
            Log.e(TAG, "IOException happened while processing the received message", e);
        }
        ONVIFDevDiscoveryReqHeader reqHeader = handler.getReqHeader();
        String reqMessageId = reqHeader.getMessageId();
        String reqAction = reqHeader.getAction();

        Log.d(TAG, String.format("req message Id are %s, req action are %s", reqMessageId, reqAction));

        // 返回响应的probeMatch packet
        String devId = Utilities.getDevId(context);
        Log.v(TAG, "current device ID are " + devId);

        // 我们在返回的probeMatch packet当中放入了当前设备(即IPCamera)的IP地址
        // 这样客户端(IPCamera-Viewer)就可以借助这个IP地址，直接向这个IP地址发起
        // ONVIF请求(ONVIF底层是基于HTTP协议的),然后我们自己(IPCamera)就可以处理
        // 这些请求，然后做出对应的操作,例如返回StreamUri等.
        String sendBack = Utilities.generateDeviceProbeMatchPacket1(
                devId,
                reqMessageId,
                serverIp);
        byte[] sendBuf = sendBack.getBytes();

        try {
            InetAddress probePacketAddress = probePacket.getAddress();
            int probePacketPort = probePacket.getPort();
            Log.d(TAG, "the probe packet address are " + probePacketAddress + ":" + probePacketPort);

            DatagramPacket packet = new DatagramPacket(sendBuf,
                    sendBuf.length, probePacketAddress, probePacketPort);

            Log.v(TAG, "send the ProbeMatch packet back");
            multicastSocket.send(packet);
        } catch (final Exception e) {
            Log.e(TAG, "Exception happened while we send the response packet", e);
        }
    }
}
