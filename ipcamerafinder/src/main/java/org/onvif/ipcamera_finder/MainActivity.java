package org.onvif.ipcamera_finder;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "SearchIPCamera";

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private TextView searchContentView;

    private WifiManager.MulticastLock multicastLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            multicastLock = wifiManager.createMulticastLock("multicast_dev_state");
            multicastLock.acquire();
        }

        searchContentView = findViewById(R.id.searched_content);
        findViewById(R.id.btn_search_ipcamera).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performSearch();
            }
        });
    }

    private static final int WS_DISCOVERY_PORT = 3702;
    /**
     * ONVIF组播地址"239.255.255.250"的IPV6版本
     */
    private static final String WS_DISCOVERTY_ADDRESS = "FF02::1";
//    private static final String WS_DISCOVERTY_ADDRESS = "0:0:0:0:0:ffff:efff:fffa";
//    private static final String WS_DISCOVERTY_ADDRESS = "239.255.255.250";
//    private static final String WS_DISCOVERTY_ADDRESS = "ff00:0:0:0:0:0:efff:fffa";

    private MulticastSocket createMulticastSocket(final String groupUrl, final int port) {
        if (TextUtils.isEmpty(groupUrl)) {
            Log.e(TAG, "the group url that provided are empty");
            return null;
        }
        try {
            if (multicastLock.isHeld()) {
                Log.d(TAG, "we have held the WifiMulticastLock");
            } else {
                Log.d(TAG, "fail to held the WifiMulticastLock, then user may fail to receive the Multicast message");
            }
            InetAddress groupAddress = InetAddress.getByName(groupUrl);
            MulticastSocket multicastSocket = new MulticastSocket(port);
            multicastSocket.setTimeToLive(10);
            multicastSocket.setSoTimeout(10000);
            multicastSocket.setNetworkInterface(NetworkInterface.getByName("wlan0"));
            multicastSocket.joinGroup(groupAddress);
            return multicastSocket;
        } catch (UnknownHostException e) {
            Log.e(TAG, "the given host of " + groupUrl + " are unknown", e);
        } catch (IOException e) {
            Log.e(TAG, "fail to create the mulitcast socket of " + port, e);
        }
        return null;
    }

    private void performSearch() {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "start search IPCamera");
                DatagramSocket datagramSocket;
                try {
                    datagramSocket = createMulticastSocket(WS_DISCOVERTY_ADDRESS, WS_DISCOVERY_PORT);

                    if (datagramSocket == null) {
                        Log.e(TAG, "fail to create the multicast socket");
                        return;
                    }

                    byte[] soapMsgBodyByteArr = PROBE_CONTENT.getBytes();
                    DatagramPacket packet = new DatagramPacket(soapMsgBodyByteArr,
                            soapMsgBodyByteArr.length, InetAddress.getByName(WS_DISCOVERTY_ADDRESS), WS_DISCOVERY_PORT);
                    datagramSocket.send(packet);
                } catch (final Exception e) {
                    Log.e(TAG, "Exception happened while we send out the probe packet", e);
                    return;
                }

                // 在发送出去probe packet之后，我们这里需要开始接收我们收到的反馈.
                List<ByteArrayInputStream> probeMatchStreamList = new ArrayList<>();
                Log.d(TAG, "start receive the probe response");
                for (; ; ) {
                    final int RESPONSE_BUF_LEN = 4096;
                    byte[] probeResponseBuffer = new byte[RESPONSE_BUF_LEN];
                    DatagramPacket responsePacket = new DatagramPacket(probeResponseBuffer, RESPONSE_BUF_LEN);
                    try {
                        datagramSocket.receive(responsePacket);
                    } catch (final Exception e) {
                        Log.e(TAG, "exception happened while we receive the probe response packet", e);
                        datagramSocket.close();
                    }

                    InetAddress packetAddress = responsePacket.getAddress();
                    if (packetAddress == null) {
                        Log.e(TAG, "the response packet address are null, just wait for next probe response packet");
                        break;
                    } else {
                        Log.d(TAG, "the packet address are " + packetAddress.toString());
                    }

                    int responsePacketLen = responsePacket.getLength();

                    Log.d(TAG, "response packet length are " + responsePacketLen);

                    Log.d(TAG, "the probe response packet raw content are " +
                            new String(responsePacket.getData(), responsePacket.getOffset(),
                                    responsePacket.getLength()));

                    probeMatchStreamList.add(new ByteArrayInputStream(responsePacket.getData()));

                    Log.d(TAG, "the probe match packet stream list size are " + probeMatchStreamList.size());
                    for (ByteArrayInputStream byteArrayInputStream : probeMatchStreamList) {
                        // handle the probe response packet
                        final int contentLen = byteArrayInputStream.available();
                        byte[] contentBuf = new byte[contentLen];
                        int readCount = byteArrayInputStream.read(contentBuf, 0, contentLen);
                        Log.d(TAG, "the read content are " + readCount);

                        String rawData = new String(contentBuf);
                        Log.d(TAG, "the response raw data are " + rawData);

                        // TODO: 进行后续的沟通


                    }
                }
            }
        });
    }

    private void updateContent(final String content) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                searchContentView.setText(content);
            }
        });
    }

    private static final String PROBE_CONTENT =
            "<s:Envelope xmlns:a=\"http://www.w3.org/2005/08/addressing\"\n" +
                    "    xmlns:d=\"http://docs.oasis-open.org/ws-dd/ns/discovery/2009/01\"\n" +
                    "    xmlns:i=\"http://printer.example.org/2003/imaging\"\n" +
                    "    xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\">\n" +
                    "    <s:Header>\n" +
                    "        <a:Action>" +
                    "            http://docs.oasis-open.org/ws-dd/ns/discovery/2009/01/Probe\n" +
                    "        </a:Action>\n" +
                    "        <a:MessageID>" +
                    "            urn:uuid:0a6dc791-2be6-4991-9af1-454778a1917a\n" +
                    "        </a:MessageID>\n" +
                    "        <a:To>urn:docs-oasis-open-org:ws-dd:ns:discovery:2009:01</a:To>\n" +
                    "    </s:Header>\n" +
                    "    <s:Body>\n" +
                    "        <d:Probe>\n" +
                    "            <d:Types>i:PrintBasic</d:Types>\n" +
                    "            <d:Scopes\n" +
                    "                MatchBy=\"http://docs.oasis-open.org/ws-dd/ns/discovery/2009/01/ldap\">\n" +
                    "                ldap:///ou=engineering,o=examplecom,c=us\n" +
                    "            </d:Scopes>\n" +
                    "        </d:Probe>\n" +
                    "    </s:Body>\n" +
                    "</s:Envelope>";

}
