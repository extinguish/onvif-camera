package net.majorkernelpanic.onvif.network;

import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import net.majorkernelpanic.http.TinyHttpServer;
import net.majorkernelpanic.onvif.DeviceBackBean;
import net.majorkernelpanic.spydroid.SpydroidApplication;

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
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.LinkedList;

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
 *
 * TODO: 我们根据ONVIFCameraAndroid当中的OnvifMediaStreamURI来调试获取RTSP URL的过程.
 */
public class ONVIFHttpServer extends TinyHttpServer {
    private static final String TAG = "onvifHttpServer";

    private DescriptionHandler descriptionHandler;

    protected final LinkedList<CallbackListener> mListeners = new LinkedList<>();

    @Override
    public void onCreate() {
        super.onCreate();

        descriptionHandler = new DescriptionHandler();

        addRequestHandler("/onvif*", descriptionHandler);
        // TODO: 这里需要确定ONVIF请求的数据包格式

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
     * 基于ONVIF请求协议解析的DescriptionHandler
     * 数据返回格式是application/soap+xml; charset=UFT-8
     */
    class DescriptionHandler implements HttpRequestHandler {
        private SpydroidApplication application = SpydroidApplication.getInstance();

        public DescriptionHandler() {

        }

        /**
         * 处理来自ONVIF Client的请求
         */
        @Override
        public void handle(HttpRequest httpRequest, HttpResponse httpResponse,
                           HttpContext httpContext) throws HttpException, IOException {
            Log.d(TAG, "handle request of " + httpRequest.toString());
            RequestLine requestLine = httpRequest.getRequestLine();
            String requestMethod = requestLine.getMethod();
            if (requestMethod.equals("POST")) {
                final String requestUrl = URLDecoder.decode(requestLine.getUri());
                Log.d(TAG, "the request url are " + requestUrl);
                HttpEntityEnclosingRequest post = (HttpEntityEnclosingRequest) httpRequest;
                byte[] entityContent = EntityUtils.toByteArray(post.getEntity());
                String content = new String(entityContent, Charset.forName("UTF-8"));
                DeviceBackBean deviceBackBean = application.getDeviceBackBean();
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
     * 获取设备的权限信息
     */
    public static final String ONVIF_GET_PROFILES_REQUEST = "GetProfiles";
    /**
     * 获取设备性能
     */
    public static final String ONVIF_GET_CAPABILITIES = "GetCapabilities";

}
