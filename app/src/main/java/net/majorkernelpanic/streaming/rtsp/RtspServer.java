/*
 * Copyright (C) 2011-2014 GUIGUI Simon, fyhertz@gmail.com
 *
 * This file is part of libstreaming (https://github.com/fyhertz/libstreaming)
 *
 * Spydroid is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package net.majorkernelpanic.streaming.rtsp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.majorkernelpanic.streaming.Session;
import net.majorkernelpanic.streaming.SessionBuilder;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * SpyDroid-IpCamera当中的推流过程同我们传统的推流过程不一样，
 * 我们自己实现的视频监控功能的话，我们的设备只是负责推流，即将流推送到指定的
 * 服务器当中，拉流端的话，只是负责去服务器上指定的位置进行拉流，
 * 这样的话，推流端和拉流端是完全隔离的两个单位。
 * 但是对于SpyDroid-IpCamera，是没有服务器的概念的，拉流端和推流端直接
 * 进行沟通，服务器被集成到了推流端这里。
 * 因此SpyDroid-IpCamera需要实现一个自己内部的推流服务器，也就是这里的
 * RtspServer.
 * <p>
 * RtspServer本身只是负责视频控制协议数据的传输，视频流本身通过{@link android.net.rtp.RtpStream}
 * 来传输.
 * 关于Rtsp协议本身的讲解，可也参考项目根目录当中的RTSP_n_RTP_simple_intro.md文档
 * 当中的解释.
 * <p>
 * Implementation of a subset of the RTSP protocol (RFC 2326).
 * <p>
 * It allows remote control of an android device cameras & microphone.
 * For each connected client, a Session is instantiated.
 * The Session will start or stop streams according to what the client wants.
 */
@SuppressLint("Registered")
public class RtspServer extends Service {

    public final static String TAG = "RtspServer";

    /**
     * The server name that will appear in responses.
     */
    public static String SERVER_NAME = "IPCamera RTSP Server";

    /**
     * Port used by default.
     */
    public static final int DEFAULT_RTSP_PORT = 8086;

    /**
     * Port already in use.
     */
    public final static int ERROR_BIND_FAILED = 0x00;

    /**
     * A stream could not be started.
     */
    public final static int ERROR_START_FAILED = 0x01;

    /**
     * Streaming started.
     */
    public final static int MESSAGE_STREAMING_STARTED = 0X00;

    /**
     * Streaming stopped.
     */
    public final static int MESSAGE_STREAMING_STOPPED = 0X01;

    /**
     * Key used in the SharedPreferences to store whether the RTSP server is enabled or not.
     */
    public final static String KEY_ENABLED = "rtsp_enabled";

    /**
     * Key used in the SharedPreferences for the port used by the RTSP server.
     */
    public final static String KEY_PORT = "rtsp_port";

    protected SessionBuilder mSessionBuilder;
    protected SharedPreferences mSharedPreferences;
    protected boolean mEnabled = true;
    protected int mPort = DEFAULT_RTSP_PORT;
    protected WeakHashMap<Session, Object> mSessions = new WeakHashMap<Session, Object>(2);

    private RequestListener mListenerThread;
    private final IBinder mBinder = new LocalBinder();
    private boolean mRestart = false;
    private final LinkedList<CallbackListener> mListeners = new LinkedList<CallbackListener>();

    public RtspServer() {
    }

    /**
     * Be careful: those callbacks won't necessarily be called from the ui thread !
     */
    public interface CallbackListener {
        /**
         * Called when an error occurs.
         */
        void onError(RtspServer server, Exception e, int error);

        /**
         * Called when streaming starts/stops.
         */
        void onMessage(RtspServer server, int message);
    }

    /**
     * See {@link RtspServer.CallbackListener} to check out what events will be fired once you set up a listener.
     *
     * @param listener The listener
     */
    public void addCallbackListener(CallbackListener listener) {
        synchronized (mListeners) {
            if (mListeners.size() > 0) {
                for (CallbackListener cl : mListeners) {
                    if (cl == listener) {
                        return;
                    }
                }
            }
            mListeners.add(listener);
        }
    }

    /**
     * Removes the listener.
     *
     * @param listener The listener
     */
    public void removeCallbackListener(CallbackListener listener) {
        synchronized (mListeners) {
            mListeners.remove(listener);
        }
    }

    /**
     * Returns the port used by the RTSP server.
     */
    public int getPort() {
        return mPort;
    }

    /**
     * Sets the port for the RTSP server to use.
     *
     * @param port The port
     */
    public void setPort(int port) {
        Editor editor = mSharedPreferences.edit();
        editor.putString(KEY_PORT, String.valueOf(port));
        editor.commit();
    }

    /**
     * Starts (or restart if needed, if for example the configuration
     * of the server has been modified) the RTSP server.
     */
    public void start() {
        Log.d(TAG, "mEnable ? " + mEnabled + ", mRestart ? " + mRestart);
        if (!mEnabled || mRestart) {
            Log.d(TAG, "start invoke stop the RtspServer");
            stop();
        }
        if (mEnabled && mListenerThread == null) {
            Log.d(TAG, "create new RequestListener");
            try {
                mListenerThread = new RequestListener();
            } catch (Exception e) {
                Log.e(TAG, "Exception happened while we start the RTSP Server", e);
                mListenerThread = null;
            }
        }
        mRestart = false;
    }

    /**
     * Stops the RTSP server but not the Android Service.
     * To stop the Android Service you need to call {@link android.content.Context#stopService(Intent)};
     */
    public void stop() {
        Log.d(TAG, "Stop the RTSP Server");
        if (mListenerThread != null) {
            try {
                mListenerThread.kill();
                for (Session session : mSessions.keySet()) {
                    if (session != null) {
                        if (session.isStreaming()) {
                            session.stop();
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception happened while we stop the RTSP server");
            } finally {
                mListenerThread = null;
            }
        }
    }

    /**
     * Returns whether or not the RTSP server is streaming to some client(s).
     */
    public boolean isStreaming() {
        for (Session session : mSessions.keySet()) {
            if (session != null) {
                if (session.isStreaming()) return true;
            }
        }
        return false;
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    /**
     * Returns the bandwidth consumed by the RTSP server in bits per second.
     */
    public long getBitrate() {
        long bitrate = 0;
        for (Session session : mSessions.keySet()) {
            if (session != null) {
                if (session.isStreaming()) {
                    bitrate += session.getBitrate();
                }
            }
        }
        Log.v(TAG, "the bit rate we get are " + bitrate);
        return bitrate;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        // Let's restore the state of the service
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mPort = Integer.parseInt(mSharedPreferences.getString(KEY_PORT, String.valueOf(mPort)));
        Log.d(TAG, "the port we read from SharedPreference file are : " + mPort);
        mEnabled = mSharedPreferences.getBoolean(KEY_ENABLED, mEnabled);

        // If the configuration is modified, the server will adjust
        mSharedPreferences.registerOnSharedPreferenceChangeListener(mOnSharedPreferenceChangeListener);
        Log.d(TAG, "RtspServer onCreate invoked");
        start();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "destroy RtspServer");
        stop();
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(mOnSharedPreferenceChangeListener);
    }

    private OnSharedPreferenceChangeListener mOnSharedPreferenceChangeListener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.equals(KEY_PORT)) {
                int port = Integer.parseInt(sharedPreferences.getString(KEY_PORT, String.valueOf(mPort)));
                Log.d(TAG, "SharedPreference updated, and the port value we read out are " + port);
                if (port != mPort) {
                    mPort = port;
                    mRestart = true;
                    Log.d(TAG, "port changed");
                    start();
                }
            } else if (key.equals(KEY_ENABLED)) {
                mEnabled = sharedPreferences.getBoolean(KEY_ENABLED, mEnabled);
                Log.d(TAG, "start the RtspServer");
                start();
            }
        }
    };

    /**
     * The Binder you obtain when a connection with the Service is established.
     */
    public class LocalBinder extends Binder {
        public RtspServer getService() {
            return RtspServer.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    protected void postMessage(int id) {
        synchronized (mListeners) {
            if (mListeners.size() > 0) {
                for (CallbackListener cl : mListeners) {
                    cl.onMessage(this, id);
                }
            }
        }
    }

    protected void postError(Exception exception, int id) {
        synchronized (mListeners) {
            if (mListeners.size() > 0) {
                for (CallbackListener cl : mListeners) {
                    cl.onError(this, exception, id);
                }
            }
        }
    }

    /**
     * By default the RTSP uses {@link UriParser} to parse the URI requested by the client
     * but you can change that behavior by override this method.
     *
     * @param uri    The uri that the client has requested
     * @param client The socket associated to the client
     * @return A proper session
     */
    protected Session handleRequest(String uri, Socket client) throws IllegalStateException {
        Log.d(TAG, "the request uri --> " + uri);
        Session session = UriParser.parse(uri);
        session.setOrigin(client.getLocalAddress().getHostAddress());
        if (session.getDestination() == null) {
            session.setDestination(client.getInetAddress().getHostAddress());
        }
        return session;
    }

    class RequestListener extends Thread implements Runnable {

        private final ServerSocket mServer;

        public RequestListener() throws IOException {
            try {
                mServer = new ServerSocket(mPort);
                Log.d(TAG, "start the listen for outcome request");
                start();
            } catch (BindException e) {
                // 这里创建ServerSocket失败,不一定是因为
                // 我们要绑定到的端口号被其他服务占用,也可能是因为
                // 因为安全限制的因素,导致我们无法绑定到这个端口号当中
                Log.e(TAG, "Exception happened while using port of " + mPort, e);
                postError(e, ERROR_BIND_FAILED);
                throw e;
            }
        }

        @Override
        public void run() {
            Log.i(TAG, "RTSP server listening on port " + mServer.getLocalPort());
            while (!Thread.interrupted()) {
                try {
                    new WorkerThread(mServer.accept()).start();
                } catch (SocketException e) {
                    Log.e(TAG, "Socket Exception happened", e);
                    break;
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage());
                }
            }
            Log.i(TAG, "RTSP server stopped !");
        }

        public void kill() {
            try {
                mServer.close();
            } catch (IOException e) {
                Log.e(TAG, "IOException happened while we kill the RTSP Server", e);
            }
            try {
                this.join();
            } catch (InterruptedException e) {
                Log.e(TAG, "InterruptedException happened", e);
            }
        }
    }

    // One thread per client
    class WorkerThread extends Thread implements Runnable {
        private final Socket mClient;
        private final OutputStream mOutput;
        private final BufferedReader mInput;

        // Each client has an associated session
        private Session mSession;

        public WorkerThread(final Socket client) throws IOException {
            mInput = new BufferedReader(new InputStreamReader(client.getInputStream()));
            mOutput = client.getOutputStream();
            mClient = client;
            mSession = new Session();
        }

        @Override
        public void run() {
            Request request;
            Response response;

            Log.i(TAG, "Connection from " + mClient.getInetAddress().getHostAddress());

            while (!Thread.interrupted()) {
                request = null;
                response = null;
                // Parse the request
                try {
                    request = Request.parseRequest(mInput);
                } catch (SocketException e) {
                    Log.e(TAG, "the client has left", e);
                    // Client has left
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Exception happened while we parse the client request", e);
                    // We don't understand the request :/
                    response = new Response();
                    response.status = Response.STATUS_BAD_REQUEST;
                }

                // Do something accordingly like starting the streams, sending a session description
                if (request != null) {
                    try {
                        // 处理客户端发起的请求
                        // 我们在processRequest方法之内会进行正式的处理工作　
                        // 例如打开视频流，视频流传输，视频流关闭等操作
                        // processRequest会同Session进行互操作.
                        response = processRequest(request);
                        // 请求处理结束，此时将处理得到的response
                        // 返回到client当中
                    } catch (Exception e) {
                        // This alerts the main thread that something has gone wrong in this thread
                        postError(e, ERROR_START_FAILED);
                        Log.e(TAG, e.getMessage() != null ? e.getMessage() : "An error occurred");
                        e.printStackTrace();
                        response = new Response(request);
                    }
                }

                // We always send a response
                // The client will receive an "INTERNAL SERVER ERROR" if an exception has been thrown at some point
                try {
                    if (response == null) {
                        Log.e(TAG, "the response are null");
                        break;
                    }
                    response.send(mOutput);
                } catch (IOException e) {
                    Log.e(TAG, "Response was not sent properly", e);
                    break;
                }
            }

            // Streaming stops when client disconnects
            boolean streaming = isStreaming();
            mSession.syncStop();
            if (streaming && !isStreaming()) {
                postMessage(MESSAGE_STREAMING_STOPPED);
            }
            mSession.release();

            try {
                mClient.close();
            } catch (IOException e) {
                Log.d(TAG, "Exception happened while we close the client", e);
            }

            Log.i(TAG, "Client disconnected");
        }

        Response processRequest(Request request) throws IllegalStateException, IOException {
            Response response = new Response(request);

            /* ********************************************************************************** */
            /* ********************************* Method DESCRIBE ******************************** */
            /* ********************************************************************************** */
            if (request.method.equalsIgnoreCase("DESCRIBE")) {
                Log.v(TAG, "client request method are --> DESCRIBE");
                // Parse the requested URI and configure the session
                mSession = handleRequest(request.uri, mClient);
                mSessions.put(mSession, null);
                mSession.syncConfigure();

                String requestContent = mSession.getSessionDescription();
                String requestAttributes =
                        "Content-Base: " + mClient.getLocalAddress().getHostAddress() + ":" + mClient.getLocalPort() + "/\r\n" +
                                "Content-Type: application/sdp\r\n";

                response.attributes = requestAttributes;
                response.content = requestContent;

                // If no exception has been thrown, we reply with OK
                response.status = Response.STATUS_OK;
            }

            /* ********************************************************************************** */
            /* ********************************* Method OPTIONS ********************************* */
            /* ********************************************************************************** */
            else if (request.method.equalsIgnoreCase("OPTIONS")) {
                Log.v(TAG, "client request method are --> OPTIONS");
                response.status = Response.STATUS_OK;
                response.attributes = "Public: DESCRIBE,SETUP,TEARDOWN,PLAY,PAUSE,GET_PARAMETER\r\n";
                response.status = Response.STATUS_OK;
            }

            /* ********************************************************************************** */
            /* ********************************** Method SETUP ********************************** */
            /* ********************************************************************************** */
            else if (request.method.equalsIgnoreCase("SETUP")) {
                Log.v(TAG, "client request method are --> SETUP");
                Pattern p;
                Matcher m;
                int p2, p1, ssrc, trackId, src[];
                String destination;

                p = Pattern.compile("trackID=(\\w+)", Pattern.CASE_INSENSITIVE);
                m = p.matcher(request.uri);

                if (!m.find()) {
                    response.status = Response.STATUS_BAD_REQUEST;
                    return response;
                }

                trackId = Integer.parseInt(m.group(1));

                if (!mSession.trackExists(trackId)) {
                    response.status = Response.STATUS_NOT_FOUND;
                    return response;
                }

                p = Pattern.compile("client_port=(\\d+)-(\\d+)", Pattern.CASE_INSENSITIVE);
                m = p.matcher(request.headers.get("transport"));

                if (!m.find()) {
                    int[] ports = mSession.getTrack(trackId).getDestinationPorts();
                    p1 = ports[0];
                    p2 = ports[1];
                } else {
                    p1 = Integer.parseInt(m.group(1));
                    p2 = Integer.parseInt(m.group(2));
                }

                ssrc = mSession.getTrack(trackId).getSSRC();
                src = mSession.getTrack(trackId).getLocalPorts();
                destination = mSession.getDestination();

                mSession.getTrack(trackId).setDestinationPorts(p1, p2);

                boolean streaming = isStreaming();

                // 开始streaming操作
                // 但是此时的streaming操作主要是为了用于RTP协议用于确定底层的视频传输操作具体是使用
                // TCP还是UDP当做视频传输的轨道(RTP本身是位于传输层和RTSP协议之间)
                // 具体可参考项目根目录当中的RTSP_n_RTP_simple_intro.md文档
                mSession.syncStart(trackId);
                if (!streaming && isStreaming()) {
                    postMessage(MESSAGE_STREAMING_STARTED);
                }

                response.attributes = "Transport: RTP/AVP/UDP;" + (InetAddress.getByName(destination).isMulticastAddress() ? "multicast" : "unicast") +
                        ";destination=" + mSession.getDestination() +
                        ";client_port=" + p1 + "-" + p2 +
                        ";server_port=" + src[0] + "-" + src[1] +
                        ";ssrc=" + Integer.toHexString(ssrc) +
                        ";mode=play\r\n" +
                        "Session: " + "1185d20035702ca" + "\r\n" +
                        "Cache-Control: no-cache\r\n";
                response.status = Response.STATUS_OK;

                // If no exception has been thrown, we reply with OK
                response.status = Response.STATUS_OK;
            }

            /* ********************************************************************************** */
            /* ********************************** Method PLAY *********************************** */
            /* ********************************************************************************** */
            else if (request.method.equalsIgnoreCase("PLAY")) {
                Log.v(TAG, "the client request method --> PLAY");
                String requestAttributes = "RTP-Info: ";
                if (mSession.trackExists(0)) { // 音频轨道是否存在
                    requestAttributes += "url=rtsp://" +
                            mClient.getLocalAddress().getHostAddress() + ":" +
                            mClient.getLocalPort() + "/trackID=" + 0 + ";seq=0,";
                }
                if (mSession.trackExists(1)) { // 视频轨道是否存在
                    requestAttributes += "url=rtsp://" +
                            mClient.getLocalAddress().getHostAddress() + ":" +
                            mClient.getLocalPort() + "/trackID=" + 1 + ";seq=0,";
                }
                requestAttributes = requestAttributes.substring(0, requestAttributes.length() - 1) +
                        "\r\nSession: 1185d20035702ca\r\n";

                response.attributes = requestAttributes;

                // If no exception has been thrown, we reply with OK
                response.status = Response.STATUS_OK;
            }

            /* ********************************************************************************** */
            /* ********************************** Method PAUSE ********************************** */
            /* ********************************************************************************** */
            else if (request.method.equalsIgnoreCase("PAUSE")) {
                Log.v(TAG, "the client request method --> PAUSE");
                response.status = Response.STATUS_OK;
            }

            /* ********************************************************************************** */
            /* ********************************* Method TEARDOWN ******************************** */
            /* ********************************************************************************** */
            else if (request.method.equalsIgnoreCase("TEARDOWN")) {
                Log.v(TAG, "the client request method --> TEARDOWN");
                response.status = Response.STATUS_OK;
            }

            /* ********************************************************************************** */
            /* ********************************* Method GET_PARAMETER *************************** */
            /* ********************************************************************************** */
            else if (request.method.equalsIgnoreCase("GET_PARAMETER")) {
                Log.v(TAG, "the client request method --> GET_PARAMETER");
                response.status = Response.STATUS_OK;
            }

            /* ********************************************************************************** */
            /* ********************************* Unknown method ? ******************************* */
            /* ********************************************************************************** */
            else {
                Log.e(TAG, "Command unknown: " + request);
                response.status = Response.STATUS_BAD_REQUEST;
            }
            return response;
        }
    }

    static class Request {
        // Parse method & uri
        public static final Pattern regexMethod = Pattern.compile("(\\w+) (\\S+) RTSP", Pattern.CASE_INSENSITIVE);
        // Parse a request header
        public static final Pattern rexegHeader = Pattern.compile("(\\S+):(.+)", Pattern.CASE_INSENSITIVE);

        public String method;
        public String uri;
        public HashMap<String, String> headers = new HashMap<String, String>();

        /**
         * Parse the method, uri & headers of a RTSP request
         */
        public static Request parseRequest(BufferedReader input) throws IOException, IllegalStateException, SocketException {
            Request request = new Request();
            String line;
            Matcher matcher;

            // 如果用户发送的是控请求的话，即input没有接收到任何内容的话，我们认为client是主动断开连接
            // Parsing request method & uri
            if ((line = input.readLine()) == null) {
                throw new SocketException("Client disconnected");
            }
            Log.d(TAG, "client raw request content are " + line);
            matcher = regexMethod.matcher(line);
            boolean matchResult = matcher.find();
            Log.d(TAG, "match find result are " + matchResult);
            request.method = matcher.group(1);
            request.uri = matcher.group(2);
            Log.d(TAG, "request uri are " + request.uri + ", request method are " + request.method);

            // Parsing headers of the request
            while ((line = input.readLine()) != null && line.length() > 3) {
                matcher = rexegHeader.matcher(line);
                matcher.find();
                request.headers.put(matcher.group(1).toLowerCase(Locale.US), matcher.group(2));
                Log.d(TAG, "REQUEST HEADER --> " + line);
            }
            if (line == null) {
                throw new SocketException("Client disconnected");
            }

            // It's not an error, it's just easier to follow what's happening in logcat with the request in red
            Log.e(TAG, "request --> " + request.method + " " + request.uri);

            return request;
        }
    }

    static class Response {
        // Status code definitions
        public static final String STATUS_OK = "200 OK";
        public static final String STATUS_BAD_REQUEST = "400 Bad Request";
        public static final String STATUS_NOT_FOUND = "404 Not Found";
        public static final String STATUS_INTERNAL_SERVER_ERROR = "500 Internal Server Error";

        public String status = STATUS_INTERNAL_SERVER_ERROR;
        public String content = "";
        public String attributes = "";

        private final Request mRequest;

        public Response(Request request) {
            this.mRequest = request;
        }

        public Response() {
            // Be careful if you modify the send() method because request might be null !
            mRequest = null;
        }

        public void send(OutputStream output) throws IOException {
            int seqid = -1;

            try {
                seqid = Integer.parseInt(mRequest.headers.get("cseq").replace(" ", ""));
            } catch (Exception e) {
                Log.e(TAG, "Error parsing CSeq: " + (e.getMessage() != null ? e.getMessage() : ""));
            }

            String response = "RTSP/1.0 " + status + "\r\n" +
                    "Server: " + SERVER_NAME + "\r\n" +
                    (seqid >= 0 ? ("Cseq: " + seqid + "\r\n") : "") +
                    "Content-Length: " + content.length() + "\r\n" +
                    attributes +
                    "\r\n" +
                    content;

            Log.d(TAG, response.replace("\r", ""));

            output.write(response.getBytes());
        }
    }
}
