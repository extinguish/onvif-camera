package com.adasplus.ipcamera;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.MediaCodecInfo;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.MemoryFile;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;

import com.adasplus.ipcamera.encoder.KyEncoderWrapper;
import com.adasplus.kylauncher.IAdasResultListener;

import net.majorkernelpanic.spydroid.R;

import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static net.majorkernelpanic.spydroid.Utilities.getMemoryFile;

/**
 * 从ShareBuffer当中读取出原始的YUV数据，然后将数据传递给Native层MediaCodec，编码成h264.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class ShareBufferReadService extends Service {
    private static final String TAG = "ShareBufferReadService";

    private static final boolean DEBUG = true;

    private AdasServiceConnection adasServiceConnection;
    private Intent bindAdasServiceIntent;
    private KyEncoderWrapper.EncodeParam encodeParam;
    private MemoryFile frontCameraMemFile;

    private static final int FRAME_WIDTH = 320;
    private static final int FRAME_HEIGHT = 240;
    private static final int ENCODE_FRAME_RATE = 15;
    private static final long ENCODE_BIT_RATE = 135000L;

    private static final int ENCODING_FORMAT = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
    private Handler frontCameraReadingHandler;
    private volatile KyEncoderWrapper frontCameraKyEncoder;

    private Executor executor;

    @Override
    public void onCreate() {
        super.onCreate();
        bindAdasServiceIntent = new Intent();
        bindAdasServiceIntent.setAction("com.adasplus.dfw");
        bindAdasServiceIntent.setPackage("com.adasplus.kylauncher");

        // yuv420编码格式
        encodeParam = new KyEncoderWrapper.EncodeParam(FRAME_WIDTH, FRAME_HEIGHT, ENCODE_FRAME_RATE,
                ENCODING_FORMAT, ENCODE_BIT_RATE);

        FrontCameraHandlerCallback frontCameraHandlerCallback = new FrontCameraHandlerCallback(new WeakReference<>(this));
        HandlerThread frontCameraHandlerThread = new HandlerThread("read_front_cam_stream_data");
        frontCameraHandlerThread.start();
        frontCameraReadingHandler = new Handler(frontCameraHandlerThread.getLooper(), frontCameraHandlerCallback);

        executor = Executors.newSingleThreadExecutor();
    }

    private void startInForeground() {
        if (DEBUG) {
            Log.v(TAG, "start the RtmpService in foreground");
        }
        Notification.Builder foregroundNotificationBuilder =
                new Notification.Builder(this)
                        .setAutoCancel(false)
                        .setOngoing(true)
                        .setContentTitle("streaming")
                        .setContentText("push streaming")
                        .setSmallIcon(R.drawable.icon)
                        .setPriority(Notification.PRIORITY_HIGH);

        startForeground(1338, foregroundNotificationBuilder.build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startInForeground();
        startEncodeShareBufferData();
        return START_STICKY;
    }

    public void startEncodeShareBufferData() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "start the rtmp service");
                // 我们绑定AdasService的目的是为了获取到前路摄像头和后路摄像头对应的MemoryFile,
                // 如果我们已经获取到了该文件，是不需要重复绑定到该Service当中的，直接处理对应的Action
                // 就可以了
                if (frontCameraMemFile != null) {
                    if (DEBUG) {
                        Log.d(TAG, "we have already connect to the front camera memory file and back camera memory file, " +
                                "do not need to connect again");
                    }
                    handleCameraStreamInfo();
                } else {
                    if (DEBUG) {
                        Log.d(TAG, "we need to connect to the remote AdasService");
                    }
                    bindRemoteAdasService(new BindRemoteAdasServiceResultListener() {
                        @Override
                        public void onGetCameraFileResult(boolean success) {
                            if (success) {
                                if (DEBUG) {
                                    Log.d(TAG, "successful in get the camera info");
                                }
                                handleCameraStreamInfo();
                            } else {
                                if (DEBUG) {
                                    Log.e(TAG, "Fail to get the camera info, and cannot performing the streaming action");
                                }
                            }
                        }
                    });
                }
            }
        });
    }

    private void handleCameraStreamInfo() {
        if (DEBUG) {
            Log.d(TAG, "trying to init the kyEncoder ");
        }
        if (frontCameraKyEncoder == null) {
            frontCameraKyEncoder = new KyEncoderWrapper();

            frontCameraKyEncoder.setEncodeParams(encodeParam);
            boolean prepareResult = frontCameraKyEncoder.prepare();
            if (!prepareResult) {
                if (DEBUG) {
                    Log.e(TAG, "prepare front camera encoder failed");
                }
                return;
            }
            boolean startResult = frontCameraKyEncoder.start();
            if (!startResult) {
                if (DEBUG) {
                    Log.e(TAG, "fail to start the front camera encoder, and try again 1 seconds later");
                }
            } else {
                if (DEBUG) {
                    Log.d(TAG, "get the front camera KyEncoder instance success, and cancel the timer");
                }
                // 获取encoder成功,开始编码
                frontCameraReadingHandler.sendEmptyMessage(MSG_READ_FRONT_STREAM_DATA);
            }
        } else {
            if (DEBUG) {
                Log.e(TAG, "the front camera are already created, may be for the cause that previous camera not stopped correctly");
            }
            // 首先停止前路摄像头的推流
            // stopStreamingPush(FRONT_CAMERA);
            // 然后1秒钟之后进行重试
        }
    }

    private void bindRemoteAdasService(final BindRemoteAdasServiceResultListener bindResultListener) {
        adasServiceConnection = new AdasServiceConnection(bindResultListener);
        bindService(bindAdasServiceIntent, adasServiceConnection, BIND_AUTO_CREATE);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    interface BindRemoteAdasServiceResultListener {
        void onGetCameraFileResult(boolean success);
    }

    /**
     * 前置摄像头的传输大小
     */
    private static final int FRONT_CAMERA_WIDTH = 640;
    private static final int FRONT_CAMERA_HEIGHT = 480;

    private static final int FRONT_CAMERA_FRAME_LEN = FRONT_CAMERA_WIDTH * FRONT_CAMERA_HEIGHT * 3 / 2;

    private class AdasServiceConnection implements ServiceConnection {
        private BindRemoteAdasServiceResultListener bindRemoteAdasServiceResultListener;

        AdasServiceConnection(BindRemoteAdasServiceResultListener bindRemoteAdasServiceResultListener) {
            this.bindRemoteAdasServiceResultListener = bindRemoteAdasServiceResultListener;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.v(TAG, "connect to the IAdasResultListener service success");
            IAdasResultListener adasResultService = IAdasResultListener.Stub.asInterface(service);

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

            if (frontCameraMemFile != null) {
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

    private static final byte[] FRONT_CAMERA_DATA_BUF = new byte[FRONT_CAMERA_FRAME_LEN];

    private void processingFrontCameraData() {
        try {
            final int readBytesCount = frontCameraMemFile.readBytes(FRONT_CAMERA_DATA_BUF, 1, 0, FRONT_CAMERA_FRAME_LEN);
            if (DEBUG) {
                Log.d(TAG, "encode : FRONT CAMERA --> with bytes length of " + readBytesCount);
            }

            if (frontCameraKyEncoder == null) {
                if (DEBUG) {
                    throw new IllegalStateException("The Front Camera KyEncoder should not be null");
                }
                Log.e(TAG, "the front camera encoder are null");
                return;
            }

            boolean encodeFrontCameraFrameResult = frontCameraKyEncoder.encodeFrame(FRONT_CAMERA_DATA_BUF, FRONT_CAMERA_WIDTH, FRONT_CAMERA_HEIGHT);
            if (!encodeFrontCameraFrameResult) {
                Log.e(TAG, "fail to encode front camera frame of front camera");
                // 当encoder停止之后，native层已经将encoder stop和release了
                // 我们只需要将Java层的Handler停止
                if (frontCameraReadingHandler != null) {
                    // frontCameraReadingHandler.removeMessages(MSG_READ_FRONT_STREAM_DATA);
                    frontCameraReadingHandler.removeCallbacksAndMessages(null);
                }
                frontCameraKyEncoder = null;
                // 同adasService解除绑定
                unbindAdasService();
            }
        } catch (IOException e) {
            if (DEBUG) {
                Log.e(TAG, "IO exception happened while read data from Camera Memory File", e);
            }
        } catch (IndexOutOfBoundsException e) {
            if (DEBUG) {
                Log.e(TAG, "index out of bounds", e);
            }
        }
    }

    private void stopStreamingPush() {
        Log.i(TAG, "----->stop streaming Adas camera <-----");
        // 首先停止Handler发送消息
        if (frontCameraReadingHandler != null) {
            // frontCameraReadingHandler.removeMessages(MSG_READ_FRONT_STREAM_DATA);
            frontCameraReadingHandler.removeCallbacksAndMessages(null);
        }
        stopEncoding(frontCameraKyEncoder);
        if (frontCameraKyEncoder != null) {
            frontCameraKyEncoder = null;
        }
        unbindAdasService();
    }

    private void stopEncoding(KyEncoderWrapper kyEncoderWrapper) {
        if (kyEncoderWrapper != null) {
            boolean stopResult = kyEncoderWrapper.stop();
            if (!stopResult) {
                Log.e(TAG, "fail to stop the encoder");
            }
            boolean releaseResult = kyEncoderWrapper.release();
            if (!releaseResult) {
                Log.e(TAG, "fail to release the encoder");
            }
            boolean destroyResult = kyEncoderWrapper.destroy();
            if (!destroyResult) {
                Log.e(TAG, "fail to destroy the encoder");
            }
        } else {
            // 可能是用户没有开始推流,就直接点击关闭推流了.对于这种情况，不做处理
            if (DEBUG) {
                Log.d(TAG, "the KyEncoder are null, do nothing");
            }
        }
    }

    private void unbindAdasService() {
        // 解除绑定　
        if (adasServiceConnection == null) {
            Log.i(TAG, "the adas service connection do not created yet");
            return;
        }
        try {
            unbindService(adasServiceConnection);
        } catch (final Exception e) {
            if (DEBUG) {
                Log.e(TAG, "exception happened while unbind the rtmp service");
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopStreamingPush();
    }

    private static final int MSG_READ_FRONT_STREAM_DATA = 1 << 1;
    private static final int READ_MEDIA_STREAMING_DATA_INTERVAL = 66;

    private static class FrontCameraHandlerCallback implements Handler.Callback {
        private ShareBufferReadService outObj;

        FrontCameraHandlerCallback(WeakReference<ShareBufferReadService> outRef) {
            this.outObj = outRef.get();
        }

        @Override
        public boolean handleMessage(Message msg) {
            if (msg.what == MSG_READ_FRONT_STREAM_DATA) {
                // 首先将消息发送出去
                // 因为outObj.processShareBufferData();方法的执行会消耗时间
                // 所以需要先将消息发送出去，然后再处理数据
                outObj.frontCameraReadingHandler.sendEmptyMessageDelayed(MSG_READ_FRONT_STREAM_DATA,
                        READ_MEDIA_STREAMING_DATA_INTERVAL);
                Log.d(TAG, "received message to read front camera data");
                outObj.processingFrontCameraData();
            }
            return false;
        }
    }
}
