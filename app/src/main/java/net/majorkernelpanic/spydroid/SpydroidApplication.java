/*
 * Copyright (C) 2011-2013 GUIGUI Simon, fyhertz@gmail.com
 *
 * This file is part of Spydroid (http://code.google.com/p/spydroid-ipcamera/)
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

package net.majorkernelpanic.spydroid;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.IBinder;
import android.os.MemoryFile;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;

import com.adasplus.ipcamera.ShareBufferReadService;
import com.adasplus.kylauncher.IAdasResultListener;

import net.majorkernelpanic.onvif.DeviceBackBean;
import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.video.VideoQuality;

import org.acra.annotation.ReportsCrashes;

import java.io.FileDescriptor;

import static net.majorkernelpanic.spydroid.Utilities.getMemoryFile;
import static org.acra.ReportField.ANDROID_VERSION;
import static org.acra.ReportField.APP_VERSION_NAME;
import static org.acra.ReportField.BRAND;
import static org.acra.ReportField.DEVICE_FEATURES;
import static org.acra.ReportField.LOGCAT;
import static org.acra.ReportField.PHONE_MODEL;
import static org.acra.ReportField.PRODUCT;
import static org.acra.ReportField.SHARED_PREFERENCES;
import static org.acra.ReportField.STACK_TRACE;
import static org.acra.ReportField.USER_APP_START_DATE;
import static org.acra.ReportField.USER_CRASH_DATE;

@ReportsCrashes(formKey = "dGhWbUlacEV6X0hlS2xqcmhyYzNrWlE6MQ", customReportContent = {APP_VERSION_NAME, PHONE_MODEL, BRAND, PRODUCT, ANDROID_VERSION, STACK_TRACE, USER_APP_START_DATE, USER_CRASH_DATE, LOGCAT, DEVICE_FEATURES, SHARED_PREFERENCES})
public class SpydroidApplication extends android.app.Application {

    public final static String TAG = "SpydroidApplication";

    static {
        // 用于实现IPCamera的so文件
        System.loadLibrary("adas_ipcamera");
    }

    /**
     * 是否是使用系统当中的ShareBuffer数据.
     * 默认使用的是系统当中的Camera的数据.
     *
     * FIXME: guoshichao 目前这里的实现不太好,是通过一个单独的全局变量来控制. 正常的好的实现,应该是通过两种单独的状态来进行控制.
     */
    public static final boolean USE_SHARE_BUFFER_DATA = false;

    public static final boolean USE_NATIVE_RTSP_SERVER = true;
    /**
     * Default quality of video streams.
     */
    public VideoQuality videoQuality = new VideoQuality(320, 240, 20, 500000);

    /**
     * By default AMR is the audio encoder.
     */
    public int audioEncoder = SessionBuilder.AUDIO_AAC;

    /**
     * By default H.263 is the video encoder.
     */
    public int videoEncoder = SessionBuilder.VIDEO_H264;

    /**
     * If the notification is enabled in the status bar of the phone.
     */
    public boolean notificationEnabled = true;

    /**
     * The HttpServer will use those variables to send reports about the state of the app to the web interface.
     */
    public boolean applicationForeground = true;
    public Exception lastCaughtException = null;

    /**
     * Contains an approximation of the battery level.
     */
    public int batteryLevel = 0;

    private static SpydroidApplication sApplication;

    @Override
    public void onCreate() {

        // The following line triggers the initialization of ACRA
        // Please do not uncomment this line unless you change the form id or I will receive your crash reports !
        //ACRA.init(this);

        sApplication = this;

        super.onCreate();

        if (USE_SHARE_BUFFER_DATA) {
            bindAdasServiceIntent = new Intent();
            bindAdasServiceIntent.setAction("com.adasplus.dfw");
            bindAdasServiceIntent.setPackage("com.adasplus.kylauncher");

            bindRemoteAdasService(new BindRemoteAdasServiceResultListener() {
                @Override
                public void onGetCameraFileResult(boolean success) {
                    Log.d(TAG, "get system share buffer memory file descriptor --> " + success);
                }
            });
        }

        if (USE_NATIVE_RTSP_SERVER) {
            // 读取ShareBuffer当中的yuv数据进行编码.
            Intent shareBufferReadServiceIntent = new Intent(this, ShareBufferReadService.class);
            startService(shareBufferReadServiceIntent);
        }

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

        notificationEnabled = settings.getBoolean("notification_enabled", true);

        // On android 3.* AAC ADTS is not supported so we set the default encoder to AMR-NB, on android 4.* AAC is the default encoder
        audioEncoder = (Integer.parseInt(android.os.Build.VERSION.SDK) < 14) ? SessionBuilder.AUDIO_AMRNB : SessionBuilder.AUDIO_AAC;
        audioEncoder = Integer.parseInt(settings.getString("audio_encoder", String.valueOf(audioEncoder)));
        videoEncoder = Integer.parseInt(settings.getString("video_encoder", String.valueOf(videoEncoder)));

        // Read video quality settings from the preferences
        videoQuality = new VideoQuality(
                settings.getInt("video_resX", videoQuality.resX),
                settings.getInt("video_resY", videoQuality.resY),
                Integer.parseInt(settings.getString("video_framerate", String.valueOf(videoQuality.framerate))),
                Integer.parseInt(settings.getString("video_bitrate", String.valueOf(videoQuality.bitrate / 1000))) * 1000);

        // 在这里我们将默认推送videoStream,而不是audioStream
        SessionBuilder.getInstance()
                .setContext(getApplicationContext())
                .setAudioEncoder(!settings.getBoolean("stream_audio", false) ? 0 : audioEncoder)
                .setVideoEncoder(!settings.getBoolean("stream_video", true) ? 0 : videoEncoder)
                .setVideoQuality(videoQuality);

        // Listens to changes of preferences
        settings.registerOnSharedPreferenceChangeListener(mOnSharedPreferenceChangeListener);

        registerReceiver(mBatteryInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    public static SpydroidApplication getInstance() {
        return sApplication;
    }

    private OnSharedPreferenceChangeListener mOnSharedPreferenceChangeListener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            switch (key) {
                case "video_resX":
                case "video_resY":
                    videoQuality.resX = sharedPreferences.getInt("video_resX", 0);
                    videoQuality.resY = sharedPreferences.getInt("video_resY", 0);
                    break;
                case "video_framerate":
                    videoQuality.framerate = Integer.parseInt(sharedPreferences.getString("video_framerate", "0"));
                    break;
                case "video_bitrate":
                    videoQuality.bitrate = Integer.parseInt(sharedPreferences.getString("video_bitrate", "0")) * 1000;
                    break;
                case "audio_encoder":
                case "stream_audio":
                    audioEncoder = Integer.parseInt(sharedPreferences.getString("audio_encoder", String.valueOf(audioEncoder)));
                    SessionBuilder.getInstance().setAudioEncoder(audioEncoder);
                    if (!sharedPreferences.getBoolean("stream_audio", false))
                        SessionBuilder.getInstance().setAudioEncoder(0);
                    break;
                case "stream_video":
                case "video_encoder":
                    videoEncoder = Integer.parseInt(sharedPreferences.getString("video_encoder", String.valueOf(videoEncoder)));
                    SessionBuilder.getInstance().setVideoEncoder(videoEncoder);
                    if (!sharedPreferences.getBoolean("stream_video", true))
                        SessionBuilder.getInstance().setVideoEncoder(0);
                    break;
                case "notification_enabled":
                    notificationEnabled = sharedPreferences.getBoolean("notification_enabled", true);
                    break;
            }
        }
    };

    private BroadcastReceiver mBatteryInfoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            batteryLevel = intent.getIntExtra("level", 0);
        }
    };

    private AdasServiceConnection adasServiceConnection;
    private Intent bindAdasServiceIntent;

    private void bindRemoteAdasService(final BindRemoteAdasServiceResultListener bindResultListener) {
        adasServiceConnection = new AdasServiceConnection(bindResultListener);
        bindService(bindAdasServiceIntent, adasServiceConnection, BIND_AUTO_CREATE);
    }

    interface BindRemoteAdasServiceResultListener {
        void onGetCameraFileResult(boolean success);
    }

    /**
     * 前置摄像头的传输大小
     */
    public static final int FRONT_CAMERA_WIDTH = 640;
    public static final int FRONT_CAMERA_HEIGHT = 480;

    /**
     * 后置摄像头的传输大小
     */
    public static final int BACK_CAMERA_WIDTH = 640;
    public static final int BACK_CAMERA_HEIGHT = 360;

    public static final int FRONT_CAMERA_FRAME_LEN = FRONT_CAMERA_WIDTH * FRONT_CAMERA_HEIGHT * 3 / 2;
    public static final int BACK_CAMERA_FRAME_LEN = BACK_CAMERA_WIDTH * BACK_CAMERA_HEIGHT * 3 / 2;

    private MemoryFile frontCameraMemFile, backCameraMemFile;

    private class AdasServiceConnection implements ServiceConnection {
        private BindRemoteAdasServiceResultListener bindRemoteAdasServiceResultListener;

        AdasServiceConnection(BindRemoteAdasServiceResultListener bindRemoteAdasServiceResultListener) {
            this.bindRemoteAdasServiceResultListener = bindRemoteAdasServiceResultListener;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.v(TAG, "connect to the IAdasResultListener service success");
            IAdasResultListener adasResultService = IAdasResultListener.Stub.asInterface(service);

            ParcelFileDescriptor backCameraInfo = null;
            try {
                backCameraInfo = adasResultService.getBackFileDescriptor();
            } catch (RemoteException e) {
                Log.e(TAG, "exception happened while get the back camera file descriptor ", e);
            }

            if (backCameraInfo != null) {
                FileDescriptor backCameraFileDescriptor = backCameraInfo.getFileDescriptor();
                backCameraMemFile = getMemoryFile(backCameraFileDescriptor, BACK_CAMERA_FRAME_LEN + 1);
                if (backCameraMemFile == null) {
                    Log.e(TAG, "fail to get the back camera memory file");
                }
            } else {
                Log.w(TAG, "fail to get the BACK CAMERA info");
            }

            ParcelFileDescriptor frontCameraInfo = null;
            try {
                frontCameraInfo = adasResultService.getFrontFileDescriptor();
            } catch (RemoteException e) {
                Log.e(TAG, "exception happened while get the front camera file descriptor", e);
            }

            // 前置摄像头用于完成Adas功能
            if (frontCameraInfo != null) {
                FileDescriptor frontCameraFileDescriptor = frontCameraInfo.getFileDescriptor();
                frontCameraMemFile = getMemoryFile(frontCameraFileDescriptor, FRONT_CAMERA_FRAME_LEN + 1);
                if (frontCameraMemFile == null) {
                    Log.e(TAG, "fail to get the FRONT CAMERA memory file");
                }
            } else {
                Log.w(TAG, "fail to get the front camera data");
            }

            if (frontCameraMemFile != null && backCameraMemFile != null) {
                this.bindRemoteAdasServiceResultListener.onGetCameraFileResult(true);
            } else {
                this.bindRemoteAdasServiceResultListener.onGetCameraFileResult(false);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "lose the connection to remote IAdasResultListener service");
        }
    }

    public MemoryFile getFrontCameraMemFile() {
        return frontCameraMemFile;
    }
}
