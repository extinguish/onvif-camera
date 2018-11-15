package net.majorkernelpanic.onvif.network;

import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import net.majorkernelpanic.http.TinyHttpServer;
import net.majorkernelpanic.onvif.DeviceBackBean;
import net.majorkernelpanic.spydroid.SpydroidApplication;
import net.majorkernelpanic.spydroid.api.CustomHttpServer;
import net.majorkernelpanic.spydroid.api.RequestHandler;
import net.majorkernelpanic.streaming.Session;

import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.RequestLine;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.WeakHashMap;

/**
 * 用于实现ONVIF的基础请求服务.
 * <p>
 * 首先ONVIF协议的实现也是基于HTTP协议的，因为所有的数据包最终底层还是通过HTTP协议发送的。
 * ONVIF协议是在HTTP协议的基础之上，对数据进行了再次封装(按照WebService的xml格式包装).
 * 因此我们将{@link ONVIFHttpServer}实现成{@link TinyHttpServer}的子类.
 * {@link ONVIFHttpServer}需要的HTTP服务由父类提供，自己只是负责实现ONVIF数据的请求
 * 和处理以及返回。
 * <p>
 * 在正式的开发环境当中，都是通过使用gSOAP工具来支持开发的，因为所有的请求都是封装到SOAP协议包当中的，
 * 如果我们选择手动实现的话，会包含很多重复的代码。
 * <p>
 * -------------------------------------------------------------------------------------
 * 这里的主要参考{@link net.majorkernelpanic.spydroid.api.CustomHttpServer}来实现.
 * 其中{@link net.majorkernelpanic.spydroid.api.CustomHttpServer}的主要实现是正对网页版的操作.
 * 在原始的SpyDroid实现当中，SpyDroid支持两种形式的视频播放，一种是直接通过vlc进行播放，还有一种
 * 是通过Browser来播放.
 * 在通过VLC播放时，那基本上就是只能看，不能进行其他类型的操作.
 * 但是通过Browser来观看时，可以进行一些其他类型的操作，例如控制视频的音量等信息.
 * 而通过Browser来控制SpyDroid的操作过程本身就是将SpyDroid当做一个Http服务器.
 * 然后Browser就是客户端，CustomHttpServer来根据Browser发送过来的指令，进行具体的操作.
 * 只是CustomHttpServer本身定义的是一种"自定义"协议(CustomHttpServer内部运行一个完整的小型网站).
 * 而{@link ONVIFHttpServer}定义的是一种严格按照ONVIF-Web_Service规格来定义的一种协议。
 * 例如数据必须使用指定的xml格式来进行封装.
 * <p>
 * TODO: 我们根据ONVIFCameraAndroid当中的OnvifMediaStreamURI来调试获取RTSP URL的过程.
 *
 * <p>
 * ------------------------------------------------------------------------------------------
 * <p>
 * 我们实现视频流对接的过程，主要依赖三个接口的实现:
 * {@link ONVIFHttpServer#ONVIF_GET_PROFILES_REQUEST}
 * {@link ONVIFHttpServer#ONVIF_GET_CAPABILITIES_REQUEST}
 * {@link ONVIFHttpServer#ONVIF_GET_STREAM_URI_REQUEST}.
 * <p>
 * 基本上实现了上面的三个接口，就可以进行之后的通信过程.
 * <p>
 * TODO: 目前我们的实现并不是在{@link ONVIFHttpServer}这里，而是通过创建一个单独
 * {@link HttpRequestHandler},然后注册到{@link TinyHttpServer}当中来实现
 * 我们自定义的ONVIF协议.
 * 但是实际上，目前不确定哪种实现方式更好，所以目前将{@link ONVIFHttpServer}也保留了下来.
 */
public class ONVIFHttpServer extends TinyHttpServer {
    private static final String TAG = "onvifHttpServer";

    private DescriptionHandler mDescriptionHandler;

    protected final LinkedList<CallbackListener> mListeners = new LinkedList<>();

    private WeakHashMap<Session, Object> mSessions = new WeakHashMap<>();

    public ONVIFHttpServer() {
        CallbackListener callbackListener = new CallbackListener() {
            @Override
            public void onError(ONVIFHttpServer server, Exception e, int error) {
                Log.e(TAG, "ONVIF Http request process error Error happened with error of " + error, e);

            }

            @Override
            public void onMessage(ONVIFHttpServer server, int message) {

            }
        };

        addCallbackListener(callbackListener);

        mHttpEnabled = true;
        // TODO: 现在目前先默认关闭HTTPS,等之后将CA搞清楚之后，再添加HTTPS
        mHttpsEnabled = false;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mDescriptionHandler = new DescriptionHandler();

        addRequestHandler("/onvif*", mDescriptionHandler);
        addRequestHandler("/onvif/*", new CustomRequestHandler());
    }

    public interface CallbackListener {
        void onError(ONVIFHttpServer server, Exception e, int error);

        void onMessage(ONVIFHttpServer server, int message);
    }

    public void addCallbackListener(CallbackListener listener) {
        this.mListeners.add(listener);
    }

    public void removeCallbackListener(CallbackListener listener) {
        synchronized (mListeners) {
            mListeners.remove(listener);
        }
    }

    @Override
    public void start() {
        // 首先开启HTTP服务
        super.start();
        // 然后开始ONVIF数据的处理服务
        Log.d(TAG, "start the ONVIF data processing action");


    }

    public class LocalBinder extends Binder {
        public ONVIFHttpServer getService() {
            return ONVIFHttpServer.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * 处理来自IPCameraViewer的post请求
     */
    class CustomRequestHandler implements HttpRequestHandler {

        public CustomRequestHandler() {

        }

        @Override
        public void handle(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) throws HttpException, IOException {
            RequestLine requestLine = httpRequest.getRequestLine();
            String requestMethod = requestLine.getMethod();
            if (requestMethod.equals("POST")) {
                String requestUri = requestLine.getUri();
                Log.d(TAG, String.format("%s are POST request", requestUri));
                // 获取到请求的内容
                HttpEntityEnclosingRequest post = (HttpEntityEnclosingRequest) httpRequest;
                byte[] entityContent = EntityUtils.toByteArray(post.getEntity());
                String content = new String(entityContent, Charset.forName("UTF-8"));

                // 对于CustomHttpServer,会将content交给RequestHandler来进行处理,而
                // RequestHandler主要是用于处理来自CustomHttpServer的内容的处理


                // 返回执行结果


            }
        }
    }

    /**
     * 最多可以接收的IPCamera的客户端数目
     */
    private static final int MAX_ACCEPTED_VIEWER_NUM = 10;

    /**
     * 基于ONVIF请求协议解析的DescriptionHandler
     * 数据返回格式是application/soap+xml; charset=UFT-8
     */
    class DescriptionHandler implements HttpRequestHandler {

        private final SessionInfo[] mSessionInfoList = new SessionInfo[MAX_ACCEPTED_VIEWER_NUM];

        private class SessionInfo {
            public Session session;
            public String uri;
            public String description;
        }

        private SpydroidApplication application = SpydroidApplication.getInstance();

        public DescriptionHandler() {
            for (int i = 0; i < MAX_ACCEPTED_VIEWER_NUM; ++i) {
                mSessionInfoList[i] = new SessionInfo();
            }
        }

        /**
         * 处理来自ONVIF Client的请求
         * <p>
         * 用户发送的请求当中，包含了用户名和密码
         */
        @Override
        public void handle(HttpRequest httpRequest, HttpResponse httpResponse,
                           HttpContext httpContext) throws HttpException, IOException {
            Socket socket = ((TinyHttpServer.MHttpContext) httpContext).getSocket();
            String uri = httpRequest.getRequestLine().getUri();

            Log.d(TAG, "handle request of " + uri);
            RequestLine requestLine = httpRequest.getRequestLine();
            String requestMethod = requestLine.getMethod();
            if (requestMethod.equals("POST")) {
                final String requestUrl = URLDecoder.decode(requestLine.getUri());
                Log.d(TAG, "the request url are " + requestUrl);
                HttpEntityEnclosingRequest post = (HttpEntityEnclosingRequest) httpRequest;
                byte[] entityContent = EntityUtils.toByteArray(post.getEntity());
                String content = new String(entityContent, Charset.forName("UTF-8"));
                DeviceBackBean deviceBackBean = DeviceBackBean.getDeviceBackBean();
                Log.d(TAG, "the request back data are " + deviceBackBean.toString());

                // 返回给client的内容
                String backContent = "";
                // 将来自Client的请求返回给Client
                if (content.contains(ONVIF_GET_DEVICE_INFORMATION_REQUEST)) {
                    // 这里处理的GetDeviceInformation请求
                    Log.d(TAG, "handle the request of get device information");
                    // TODO: 这里构造相应的xml数据，然后返回
                    backContent = "";
                } else if (content.contains(ONVIF_GET_STREAM_URI_REQUEST)) {
                    Log.d(TAG, "handle the request of get stream url");
                    backContent = "";
                } else if (content.contains(ONVIF_GET_PROFILES_REQUEST)) {
                    Log.d(TAG, "handle the request of get device profile");
                    backContent = "";
                } else {
                    // TODO: do not handle this response
                    return;
                }
                Log.d(TAG, "the response info are " + backContent);
                final String finalBackContent = backContent;
                ByteArrayEntity backBody = new ByteArrayEntity(finalBackContent.getBytes());
                ByteArrayInputStream backInputStream = (ByteArrayInputStream) backBody.getContent();
                httpResponse.setStatusCode(HttpStatus.SC_OK);
                backBody.setContentType("application/soap+xml; charset=UTF-8");
                httpResponse.setEntity(backBody);
            }
        }
    }

    /**
     * 以下是主要的接口，根据支持的Profile不同，实现的接口种类也不同.
     * <p>
     * <p>
     * 获取设备信息的请求
     */
    public static final String ONVIF_GET_DEVICE_INFORMATION_REQUEST = "GetDeviceInformation";
    /**
     * 获取视频流的请求
     * 我们不在需要对返回的视频流再做第二层处理，客户端直接使用RTSP协议解析就可以
     */
    public static final String ONVIF_GET_STREAM_URI_REQUEST = "GetStreamUri";
    /**
     * 获取设备的配置信息
     */
    public static final String ONVIF_GET_PROFILES_REQUEST = "GetProfiles";
    /**
     * 获取设备性能
     */
    public static final String ONVIF_GET_CAPABILITIES_REQUEST = "GetCapabilities";

    /**
     * 获取IPCamera的编码器配置
     */
    public static final String ONVIF_GET_VIDEO_ENCODER_CONFIGURATION_REQUEST = "GetVideoEncoderConfiguration";

    /**
     * 获取IPCamera的编码器配置选项
     */
    public static final String ONVIF_GET_VIDEO_ENCODER_CONFIGURATION_OPTIONS_REQUEST = "GetVideoEncoderConfigurationOptions";

}
