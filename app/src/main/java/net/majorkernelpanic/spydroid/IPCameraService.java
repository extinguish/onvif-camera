package net.majorkernelpanic.spydroid;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import net.majorkernelpanic.http.TinyHttpServer;
import net.majorkernelpanic.onvif.SimpleONVIFManager;
import net.majorkernelpanic.onvif.network.ONVIFHttpServer;
import net.majorkernelpanic.spydroid.api.CustomHttpServer;
import net.majorkernelpanic.spydroid.api.CustomRtspServer;
import net.majorkernelpanic.streaming.rtsp.RtspServer;

/**
 * 当我们被安装到Ky设备上时，我们本身是没有界面的，因此我们需要
 * 一个位于后台运行的Service来进行启动的管理
 * <p>
 * 该服务由KyLauncher统一来协调管理启动,对于普通应用的话,不需要管理该应用.
 */
public class IPCameraService extends Service {
    private static final String TAG = "IPCameraService";

    private CustomHttpServer mHttpServer;
    private RtspServer mRtspServer;
    private ONVIFHttpServer mONVIFServer;

    @Override
    public void onCreate() {
        super.onCreate();
        startIPCameraFunc();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void startIPCameraFunc() {
        // Starts the service of the HTTP server
        this.startService(new Intent(this, CustomHttpServer.class));

        // Starts the service of the RTSP server
        this.startService(new Intent(this, CustomRtspServer.class));

        // 开始 ONVIF-HTTP server
        this.startService(new Intent(this, ONVIFHttpServer.class));

        // 这里的设计并不是特别好，我们只是创建了一个SimpleONVIFManager实例而已
        new SimpleONVIFManager(this);

        bindService(new Intent(this, CustomHttpServer.class), mHttpServiceConnection, Context.BIND_AUTO_CREATE);
        bindService(new Intent(this, CustomRtspServer.class), mRtspServiceConnection, Context.BIND_AUTO_CREATE);
        // 绑定到ONVIFHttpService
        bindService(new Intent(this, ONVIFHttpServer.class), mONVIFHttpServiceConnection, Context.BIND_AUTO_CREATE);
    }

    private LocalBinder mBinder = new LocalBinder();

    /**
     * The Binder you obtain when a connection with the Service is established.
     */
    public class LocalBinder extends Binder {
        public IPCameraService getService() {
            return IPCameraService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private ServiceConnection mONVIFHttpServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "we have connected to the service of " + name.flattenToString());
            mONVIFServer = ((ONVIFHttpServer.LocalBinder) service).getService();
            mONVIFServer.addCallbackListener(mONVIFCallbackListener);
            mONVIFServer.start();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "we have disconnect from the service of " + name.flattenToString());
        }
    };

    private ONVIFHttpServer.CallbackListener mONVIFCallbackListener = new ONVIFHttpServer.CallbackListener() {
        @Override
        public void onError(ONVIFHttpServer server, Exception e, int error) {
            Log.e(TAG, "ONVIF Http Server Error happened with error of " + error, e);
        }

        @Override
        public void onMessage(ONVIFHttpServer server, int message) {
            Log.i(TAG, "ONVIF Http Server with message notified of " + message);
        }
    };

    private ServiceConnection mHttpServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "connect to the Http Service of " + name.flattenToString());
            mHttpServer = (CustomHttpServer) ((TinyHttpServer.LocalBinder) service).getService();
            mHttpServer.addCallbackListener(mHttpCallbackListener);
            mHttpServer.start();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "lost connection to the Http Service");

        }
    };

    private TinyHttpServer.CallbackListener mHttpCallbackListener = new TinyHttpServer.CallbackListener() {
        @Override
        public void onError(TinyHttpServer server, Exception e, int error) {
            Log.e(TAG, "Tiny Http Server Error happened with error of " + error, e);
        }

        @Override
        public void onMessage(TinyHttpServer server, int message) {
            Log.i(TAG, "Tiny Http Server with message notified of " + message);
        }
    };

    private ServiceConnection mRtspServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "we have connected to the " + name.flattenToString());
            mRtspServer = ((RtspServer.LocalBinder) service).getService();
            mRtspServer.addCallbackListener(mRtspCallbackListener);
            mRtspServer.start();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "we have lost the connection to " + name.flattenToString());
        }
    };

    private RtspServer.CallbackListener mRtspCallbackListener = new RtspServer.CallbackListener() {
        @Override
        public void onError(RtspServer server, Exception e, int error) {
            Log.e(TAG, "RTSP Server Error happened with error of " + error, e);
        }

        @Override
        public void onMessage(RtspServer server, int message) {
            Log.i(TAG, "RTSP Server with message notified of " + message);
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mHttpServer != null) {
            mHttpServer.removeCallbackListener(mHttpCallbackListener);
        }
        unbindService(mHttpServiceConnection);
        if (mRtspServer != null) {
            mRtspServer.removeCallbackListener(mRtspCallbackListener);
        }
        unbindService(mRtspServiceConnection);
        if (mONVIFServer != null) {
            mONVIFServer.removeCallbackListener(mONVIFCallbackListener);
        }
        unbindService(mONVIFHttpServiceConnection);
    }
}
