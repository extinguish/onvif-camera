package com.adasplus.onvif;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.util.Log;

import com.adasplus.onvif.http.CustomHttpServer;
import com.adasplus.onvif.http.TinyHttpServer;

import net.majorkernelpanic.spydroid.R;

/**
 * Using to manage the SimpleONVIFManager, and this will be the
 * only single entrance of the Onvif service.
 * <p>
 * TODO: in the following implementation, we use the gSoap to re-implement the onvif server.
 */
public class OnvifService extends Service {
    private static final String TAG = "OnvifService";
    private PowerManager.WakeLock mWakeLock;
    private CustomHttpServer mHttpServer;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startInForeground();
        startOnvifService();
        return START_STICKY;
    }

    private void startInForeground() {
        Log.i(TAG, "start the RtmpService in foreground");
        Notification.Builder foregroundNotificationBuilder =
                new Notification.Builder(this)
                        .setAutoCancel(false)
                        .setOngoing(true)
                        .setContentTitle("streaming")
                        .setContentText("push streaming")
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setPriority(Notification.PRIORITY_HIGH);
        Notification notification = foregroundNotificationBuilder.build();
        notification.flags |= Notification.FLAG_ONGOING_EVENT;

        startForeground(0, notification);
    }

    @SuppressLint("InvalidWakeLockTag")
    private void startOnvifService() {
        Log.i(TAG, "start onvif service");
        // Prevents the phone from going to sleep mode
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm == null) {
            throw new RuntimeException("Fail to get the PackageManager");
        }
        mWakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "net.majorkernelpanic.spydroid.wakelock");

        // Starts the service of the HTTP server
        this.startService(new Intent(this, CustomHttpServer.class));

        // 这里的设计并不是特别好，我们只是创建了一个SimpleONVIFManager实例而已
        // 理论上初始化方法应该同构造函数分开进行
        new SimpleONVIFManager(this);

        // Lock screen
        mWakeLock.acquire(50 * 60 * 1000L /*50 minutes*/);

        bindService(new Intent(this, CustomHttpServer.class), mHttpServiceConnection, Context.BIND_AUTO_CREATE);
    }

    private void quitOnvifService() {
        // Removes notification
        // if (mApplication.notificationEnabled) removeNotification();
        // Kills HTTP server
        this.stopService(new Intent(this, CustomHttpServer.class));
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


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
            // We alert the user that the port is already used by another app.
            if (error == TinyHttpServer.ERROR_HTTP_BIND_FAILED ||
                    error == TinyHttpServer.ERROR_HTTPS_BIND_FAILED) {
                String str = error == TinyHttpServer.ERROR_HTTP_BIND_FAILED ? "HTTP" : "HTTPS";
                Log.e(TAG, str);
            }
        }

        @Override
        public void onMessage(TinyHttpServer server, int message) {
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        // A WakeLock should only be released when isHeld() is true !
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        if (mHttpServer != null) {
            mHttpServer.removeCallbackListener(mHttpCallbackListener);
        }
        unbindService(mHttpServiceConnection);
    }
}
