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

package net.majorkernelpanic.streaming.video;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.MemoryFile;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;

import net.majorkernelpanic.spydroid.SpydroidApplication;
import net.majorkernelpanic.streaming.MediaStream;
import net.majorkernelpanic.streaming.Stream;
import net.majorkernelpanic.streaming.exceptions.CameraInUseException;
import net.majorkernelpanic.streaming.exceptions.ConfNotSupportedException;
import net.majorkernelpanic.streaming.exceptions.InvalidSurfaceException;
import net.majorkernelpanic.streaming.gl.SurfaceView;
import net.majorkernelpanic.streaming.hw.EncoderDebugger;
import net.majorkernelpanic.streaming.hw.NV21Convertor;
import net.majorkernelpanic.streaming.rtp.MediaCodecInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static net.majorkernelpanic.spydroid.SpydroidApplication.BACK_CAMERA_FRAME_LEN;
import static net.majorkernelpanic.spydroid.SpydroidApplication.FRONT_CAMERA_FRAME_LEN;

/**
 * Don't use this class directly.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public abstract class VideoStream extends MediaStream {
    protected final static String TAG = "VideoStream";

    protected VideoQuality mRequestedQuality = VideoQuality.DEFAULT_VIDEO_QUALITY.clone();
    protected VideoQuality mQuality = mRequestedQuality.clone();
    protected SurfaceHolder.Callback mSurfaceHolderCallback = null;
    protected SurfaceView mSurfaceView = null;
    protected SharedPreferences mSettings = null;
    protected int mVideoEncoder, mCameraId = 0;
    protected int mRequestedOrientation = 0, mOrientation = 0;
    protected Camera mCamera;
    protected Thread mCameraThread;
    protected Looper mCameraLooper;

    protected boolean mCameraOpenedManually = true;
    protected boolean mFlashEnabled = false;
    protected boolean mSurfaceReady = false;
    protected boolean mUnlocked = false;
    protected boolean mPreviewStarted = false;

    protected String mMimeType;
    protected String mEncoderName;
    protected int mEncoderColorFormat;
    protected int mCameraImageFormat;
    protected int mMaxFps = 0;

    private MemoryFile mFrontCameraMemFile, mBackCameraMemFile;

    /**
     * Don't use this class directly.
     * Uses CAMERA_FACING_BACK by default.
     */
    public VideoStream() {
        this(CameraInfo.CAMERA_FACING_BACK);
    }

    /**
     * Don't use this class directly
     *
     * @param camera Can be either CameraInfo.CAMERA_FACING_BACK or CameraInfo.CAMERA_FACING_FRONT
     */
    @SuppressLint("InlinedApi")
    VideoStream(int camera) {
        super();
        if (SpydroidApplication.USE_SHARE_BUFFER_DATA) {
            FrontCameraHandlerCallback frontCameraHandlerCallback = new FrontCameraHandlerCallback(new WeakReference<>(this));
            HandlerThread frontCameraHandlerThread = new HandlerThread("read_front_cam_stream_data");
            frontCameraHandlerThread.start();
            mFrontCameraReadingHandler = new Handler(frontCameraHandlerThread.getLooper(), frontCameraHandlerCallback);

            BackCameraHandlerCallback backCameraHandlerCallback = new BackCameraHandlerCallback(new WeakReference<>(this));
            HandlerThread backCameraHandlerThread = new HandlerThread("read_back_cam_stream_data");
            backCameraHandlerThread.start();
            mBackCameraReadingHandler = new Handler(backCameraHandlerThread.getLooper(), backCameraHandlerCallback);

            SpydroidApplication application = SpydroidApplication.getInstance();
            mFrontCameraMemFile = application.getFrontCameraMemFile();
            mBackCameraMemFile = application.getBackCameraMemFile();
        }

        setCamera(camera);
    }

    /**
     * Sets the camera that will be used to capture video.
     * You can call this method at any time and changes will take effect next time you start the stream.
     *
     * @param camera Can be either CameraInfo.CAMERA_FACING_BACK or CameraInfo.CAMERA_FACING_FRONT
     */
    public void setCamera(int camera) {
        CameraInfo cameraInfo = new CameraInfo();
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == camera) {
                mCameraId = i;
                break;
            }
        }
    }

    /**
     * Switch between the front facing and the back facing camera of the phone.
     * If {@link #startPreview()} has been called, the preview will be  briefly interrupted.
     * If {@link #start()} has been called, the stream will be  briefly interrupted.
     * You should not call this method from the main thread if you are already streaming.
     *
     * @throws IOException
     * @throws RuntimeException
     **/
    public void switchCamera() throws RuntimeException, IOException {
        if (Camera.getNumberOfCameras() == 1) {
            throw new IllegalStateException("Phone only has one camera !");
        }
        boolean streaming = mStreaming;
        boolean previewing = mCamera != null && mCameraOpenedManually;
        mCameraId = (mCameraId == CameraInfo.CAMERA_FACING_BACK) ?
                CameraInfo.CAMERA_FACING_FRONT : CameraInfo.CAMERA_FACING_BACK;
        setCamera(mCameraId);
        stopPreview();
        mFlashEnabled = false;
        if (previewing) {
            startPreview();
        }
        if (streaming) {
            start();
        }
    }

    public int getCamera() {
        return mCameraId;
    }

    /**
     * Sets a Surface to show a preview of recorded media (video).
     * You can call this method at any time and changes will take effect next time you call {@link #start()}.
     */
    public synchronized void setSurfaceView(SurfaceView view) {
        mSurfaceView = view;
        if (mSurfaceHolderCallback != null && mSurfaceView != null && mSurfaceView.getHolder() != null) {
            mSurfaceView.getHolder().removeCallback(mSurfaceHolderCallback);
        }
        if (mSurfaceView.getHolder() != null) {
            mSurfaceHolderCallback = new Callback() {
                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    mSurfaceReady = false;
                    stopPreview();
                    Log.d(TAG, "Surface destroyed !");
                }

                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    mSurfaceReady = true;
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                    Log.d(TAG, "Surface Changed !");
                }
            };
            mSurfaceView.getHolder().addCallback(mSurfaceHolderCallback);
            mSurfaceReady = true;
        }
    }

    /**
     * Turns the LED on or off if phone has one.
     */
    public synchronized void setFlashState(boolean state) {
        // If the camera has already been opened, we apply the change immediately
        if (mCamera != null) {
            if (mStreaming && mMode == MODE_MEDIARECORDER_API) {
                lockCamera();
            }
            Parameters parameters = mCamera.getParameters();

            // We test if the phone has a flash
            if (parameters.getFlashMode() == null) {
                // The phone has no flash or the choosen camera can not toggle the flash
                throw new RuntimeException("Can't turn the flash on !");
            } else {
                parameters.setFlashMode(state ? Parameters.FLASH_MODE_TORCH : Parameters.FLASH_MODE_OFF);
                try {
                    mCamera.setParameters(parameters);
                    mFlashEnabled = state;
                } catch (RuntimeException e) {
                    mFlashEnabled = false;
                    throw new RuntimeException("Can't turn the flash on !");
                } finally {
                    if (mStreaming && mMode == MODE_MEDIARECORDER_API) {
                        unlockCamera();
                    }
                }
            }
        } else {
            mFlashEnabled = state;
        }
    }

    /**
     * Toggle the LED of the phone if it has one.
     */
    public synchronized void toggleFlash() {
        setFlashState(!mFlashEnabled);
    }

    /**
     * Indicates whether or not the flash of the phone is on.
     */
    public boolean getFlashState() {
        return mFlashEnabled;
    }

    /**
     * Sets the orientation of the preview.
     *
     * @param orientation The orientation of the preview
     */
    public void setPreviewOrientation(int orientation) {
        mRequestedOrientation = orientation;
    }

    /**
     * Sets the configuration of the stream. You can call this method at any time
     * and changes will take effect next time you call {@link #configure()}.
     *
     * @param videoQuality Quality of the stream
     */
    public void setVideoQuality(VideoQuality videoQuality) {
        mRequestedQuality = videoQuality.clone();
    }

    /**
     * Returns the quality of the stream.
     */
    public VideoQuality getVideoQuality() {
        return mRequestedQuality;
    }

    /**
     * Some data (SPS and PPS params) needs to be stored when {@link #getSessionDescription()} is called
     *
     * @param prefs The SharedPreferences that will be used to save SPS and PPS parameters
     */
    public void setPreferences(SharedPreferences prefs) {
        mSettings = prefs;
    }

    /**
     * Configures the stream. You need to call this before calling {@link #getSessionDescription()}
     * to apply your configuration of the stream.
     */
    public synchronized void configure() throws IllegalStateException, IOException {
        super.configure();
        mOrientation = mRequestedOrientation;
    }

    /**
     * Starts the stream.
     * This will also open the camera and dispay the preview
     * if {@link #startPreview()} has not aready been called.
     */
    public synchronized void start() throws IllegalStateException, IOException {
        if (!mPreviewStarted) mCameraOpenedManually = false;
        super.start();
        Log.d(TAG, "Stream configuration: FPS: " + mQuality.framerate + " Width: " + mQuality.resX + " Height: " + mQuality.resY);
    }

    /**
     * Stops the stream.
     */
    public synchronized void stop() {
        if (mCamera != null) {
            if (mMode == MODE_MEDIACODEC_API) {
                mCamera.setPreviewCallbackWithBuffer(null);
            }
            if (mMode == MODE_MEDIACODEC_API_2) {
                mSurfaceView.removeMediaCodecSurface();
            }
            super.stop();
            // We need to restart the preview
            if (!mCameraOpenedManually) {
                destroyCamera();
            } else {
                try {
                    startPreview();
                } catch (RuntimeException e) {
                    Log.e(TAG, "Fail to stop the stream", e);
                }
            }
        }
    }

    public synchronized void startPreview() throws RuntimeException {
        mCameraOpenedManually = true;
        if (!mPreviewStarted) {
            createCamera();
            updateCamera();
            try {
                mCamera.startPreview();
                mPreviewStarted = true;
            } catch (RuntimeException e) {
                destroyCamera();
                throw e;
            }
        }
    }

    /**
     * Stops the preview.
     */
    public synchronized void stopPreview() {
        mCameraOpenedManually = false;
        stop();
    }

    /**
     * Video encoding is done by a MediaRecorder.
     */
    protected void encodeWithMediaRecorder() throws IOException {
        Log.d(TAG, "Video encoded using the MediaRecorder API");

        // We need a local socket to forward data output by the camera to the packetizer
        createSockets();

        // Reopens the camera if needed
        destroyCamera();
        createCamera();

        // The camera must be unlocked before the MediaRecorder can use it
        unlockCamera();

        try {
            mMediaRecorder = new MediaRecorder();
            mMediaRecorder.setCamera(mCamera);
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mMediaRecorder.setVideoEncoder(mVideoEncoder);
            mMediaRecorder.setPreviewDisplay(mSurfaceView.getHolder().getSurface());
            mMediaRecorder.setVideoSize(mRequestedQuality.resX, mRequestedQuality.resY);
            mMediaRecorder.setVideoFrameRate(mRequestedQuality.framerate);

            // The bandwidth actually consumed is often above what was requested
            mMediaRecorder.setVideoEncodingBitRate((int) (mRequestedQuality.bitrate * 0.8));

            // We write the output of the camera in a local socket instead of a file !
            // This one little trick makes streaming feasible quiet simply: data from the camera
            // can then be manipulated at the other end of the socket
            mMediaRecorder.setOutputFile(mSender.getFileDescriptor());

            mMediaRecorder.prepare();
            mMediaRecorder.start();

        } catch (Exception e) {
            throw new ConfNotSupportedException(e.getMessage());
        }

        // This will skip the MPEG4 header if this step fails we can't stream anything :(
        InputStream is = mReceiver.getInputStream();
        try {
            byte buffer[] = new byte[4];
            // Skip all atoms preceding mdat atom
            while (!Thread.interrupted()) {
                while (is.read() != 'm') ;
                is.read(buffer, 0, 3);
                if (buffer[0] == 'd' && buffer[1] == 'a' && buffer[2] == 't') break;
            }
        } catch (IOException e) {
            Log.e(TAG, "Couldn't skip mp4 header :/");
            stop();
            throw e;
        }

        // The packetizer encapsulates the bit stream in an RTP stream and send it over the network
        mPacketizer.setDestination(mDestination, mRtpPort, mRtcpPort);
        mPacketizer.setInputStream(mReceiver.getInputStream());
        mPacketizer.start();

        mStreaming = true;
    }

    /**
     * Video encoding is done by a MediaCodec.
     */
    @Override
    protected void encodeWithMediaCodec() throws RuntimeException {
        if (mMode == MODE_MEDIACODEC_API_2) {
            // Uses the method MediaCodec.createInputSurface to feed the encoder
            Log.v(TAG, "Use the MediaCodec.createInputSurface to feed encoder");
            try {
                encodeWithMediaCodecMethod2();
            } catch (IOException e) {
                Log.e(TAG, "fail to encode with MediaCodecMethod2", e);
            }
        } else {
            // Uses dequeueInputBuffer to feed the encoder
            Log.v(TAG, "Use the dequeueInputBuffer to feed the encoder");
            try {
                encodeWithMediaCodecMethod1();
            } catch (IOException e) {
                Log.e(TAG, "fail to encode with MediaCodecMethod1", e);
            }
        }
    }

    private NV21Convertor mConvertor;

    /**
     * Video encoding is done by a MediaCodec.
     */
    @SuppressLint("NewApi")
    private void encodeWithMediaCodecMethod1() throws RuntimeException, IOException {
        Log.d(TAG, "Video encoded using the MediaCodec API with a buffer");

        // Updates the parameters of the camera if needed
        createCamera();
        updateCamera();

        // Estimates the framerate of the camera
        measureFramerate();

        // Starts the preview if needed
        if (!mPreviewStarted) {
            try {
                mCamera.startPreview();
                mPreviewStarted = true;
            } catch (RuntimeException e) {
                destroyCamera();
                throw e;
            }
        }

        EncoderDebugger debugger = EncoderDebugger.debug(mSettings, mQuality.resX, mQuality.resY);
        mConvertor = debugger.getNV21Convertor();

        mMediaCodec = MediaCodec.createByCodecName(debugger.getEncoderName());
        MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", mQuality.resX, mQuality.resY);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mQuality.bitrate);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mQuality.framerate);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, debugger.getEncoderColorFormat());
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mMediaCodec.start();

        // TODO: 我们需要在这里进行数据源的转换
        // TODO: 这里是直接使用的CameraPreview的数据,
        // TODO: 替换成来自ShareBuffer的数据
        if (SpydroidApplication.USE_SHARE_BUFFER_DATA) {
            // 发送消息，开始读取ShareBuffer当中的数据
            mFrontCameraReadingHandler.sendEmptyMessage(MSG_READ_FRONT_STREAM_DATA);
            // 然后在processShareBufferData()当中对读取出来的数据进行编码

            // 然后开始解码的操作
            for (int i = 0; i < 10; i++) {
                mCamera.addCallbackBuffer(new byte[mConvertor.getBufferSize()]);
            }

            mPacketizer.setDestination(mDestination, mRtpPort, mRtcpPort);
            mPacketizer.setInputStream(new MediaCodecInputStream(mMediaCodec));
            mPacketizer.start();

            mStreaming = true;
        } else {
            // 使用系统相机的预览数据
            Camera.PreviewCallback callback = new Camera.PreviewCallback() {
                long now = System.nanoTime() / 1000, oldnow = now, i = 0;
                ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();

                @Override
                public void onPreviewFrame(byte[] data, Camera camera) {
                    oldnow = now;
                    now = System.nanoTime() / 1000;
                    if (i++ > 3) {
                        i = 0;
                        //Log.d(TAG,"Measured: "+1000000L/(now-oldnow)+" fps.");
                    }
                    try {
                        int bufferIndex = mMediaCodec.dequeueInputBuffer(500000);
                        if (bufferIndex >= 0) {
                            inputBuffers[bufferIndex].clear();
                            // 首先将原始预览数据进行UV通道转换
                            mConvertor.convert(data, inputBuffers[bufferIndex]); // 转换后的数据会被放到inputBuffers当中
                            // 然后再交给MediaCodec进行编码
                            // 关于MediaCodec#queueInputBuffer的作用:
                            /*
                             * After filling a range of the input buffer at the specified index
                             * submit it to the component.
                             *
                             * Many decoders require the actual compressed data stream to be
                             * preceded by "codec specific data", i.e. setup data used to initialize
                             * the codec such as PPS/SPS in the case of AVC video or code tables
                             * in the case of vorbis audio.
                             * The class {@link android.media.MediaExtractor} provides codec
                             * specific data as part of
                             * the returned track format in entries named "csd-0", "csd-1" ...
                             *
                             * These buffers should be submitted using the flag BUFFER_FLAG_CODEC_CONFIG.
                             *
                             * To indicate that this is the final piece of input data (or rather that
                             * no more input data follows unless the decoder is subsequently flushed)
                             * specify the flag BUFFER_FLAG_END_OF_STREAM.
                             */
                            mMediaCodec.queueInputBuffer(
                                    bufferIndex, // The index of a client-owned input buffer previously returned
                                    0, // The byte offset into the input buffer at which the data starts.
                                    inputBuffers[bufferIndex].position(), // The number of bytes of valid input data.
                                    now,  // The time at which this buffer should be rendered.
                                    0 // A bitmask of flags BUFFER_FLAG_SYNC_FRAME, BUFFER_FLAG_CODEC_CONFIG
                                    // or BUFFER_FLAG_END_OF_STREAM.
                            );
                        } else {
                            Log.e(TAG, "No buffer available !");
                        }
                    } finally {
                        mCamera.addCallbackBuffer(data);
                    }
                }
            };

            for (int i = 0; i < 10; i++) {
                mCamera.addCallbackBuffer(new byte[mConvertor.getBufferSize()]);
            }
            mCamera.setPreviewCallbackWithBuffer(callback);

            // The packetizer encapsulates the bit stream in an RTP stream and send it over the network
            mPacketizer.setDestination(mDestination, mRtpPort, mRtcpPort);
            mPacketizer.setInputStream(new MediaCodecInputStream(mMediaCodec));
            mPacketizer.start();

            mStreaming = true;
        }
    }

    /**
     * Video encoding is done by a MediaCodec.
     * But here we will use the buffer-to-surface methode
     */
    @SuppressLint({"InlinedApi", "NewApi"})
    private void encodeWithMediaCodecMethod2() throws RuntimeException, IOException {
        Log.d(TAG, "Video encoded using the MediaCodec API with a surface");

        // Updates the parameters of the camera if needed
        createCamera();
        updateCamera();

        // Estimates the framerate of the camera
        measureFramerate();

        EncoderDebugger debugger = EncoderDebugger.debug(mSettings, mQuality.resX, mQuality.resY);

        mMediaCodec = MediaCodec.createByCodecName(debugger.getEncoderName());
        MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", mQuality.resX, mQuality.resY);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mQuality.bitrate);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mQuality.framerate);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        // Requests a Surface to use as the input to an encoder, in place of input buffers.
        Surface surface = mMediaCodec.createInputSurface();
        mSurfaceView.addMediaCodecSurface(surface);
        mMediaCodec.start();

        // The packetizer encapsulates the bit stream in an RTP stream and send it over the network
        mPacketizer.setDestination(mDestination, mRtpPort, mRtcpPort);
        mPacketizer.setInputStream(new MediaCodecInputStream(mMediaCodec));
        mPacketizer.start();

        mStreaming = true;
    }

    /**
     * Returns a description of the stream using SDP.
     * This method can only be called after {@link Stream#configure()}.
     *
     * @throws IllegalStateException Thrown when {@link Stream#configure()} wa not called.
     */
    public abstract String getSessionDescription() throws IllegalStateException;

    /**
     * Opens the camera in a new Looper thread so that the preview callback is not called from the main thread
     * If an exception is thrown in this Looper thread, we bring it back into the main thread.
     *
     * @throws RuntimeException Might happen if another app is already using the camera.
     */
    private void openCamera() throws RuntimeException {
        final Semaphore lock = new Semaphore(0);
        final RuntimeException[] exception = new RuntimeException[1];
        mCameraThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                mCameraLooper = Looper.myLooper();
                try {
                    mCamera = Camera.open(mCameraId);
                } catch (RuntimeException e) {
                    exception[0] = e;
                } finally {
                    lock.release();
                    Looper.loop();
                }
            }
        });
        mCameraThread.start();
        lock.acquireUninterruptibly();
        if (exception[0] != null) {
            throw new CameraInUseException(exception[0].getMessage());
        }
    }

    synchronized void createCamera() throws RuntimeException {
        Log.v(TAG, "create camera");
        if (mSurfaceView == null) {
            throw new InvalidSurfaceException("Invalid surface !");
        }
        if (mSurfaceView.getHolder() == null || !mSurfaceReady) {
            throw new InvalidSurfaceException("Invalid surface !");
        }

        if (mCamera == null) {
            openCamera();
            mUnlocked = false;
            mCamera.setErrorCallback(new Camera.ErrorCallback() {
                @Override
                public void onError(int error, Camera camera) {
                    // On some phones when trying to use the camera facing front the media server will die
                    // Whether or not this callback may be called really depends on the phone
                    if (error == Camera.CAMERA_ERROR_SERVER_DIED) {
                        // In this case the application must release the camera and instantiate a new one
                        Log.e(TAG, "Media server died !");
                        // We don't know in what thread we are so stop needs to be synchronized
                        mCameraOpenedManually = false;
                        stop();
                    } else {
                        Log.e(TAG, "Error unknown with the camera: " + error);
                    }
                }
            });

            try {
                // If the phone has a flash, we turn it on/off according to mFlashEnabled
                // setRecordingHint(true) is a very nice optimisation if you plane to only use the Camera for recording
                Parameters parameters = mCamera.getParameters();
                if (parameters.getFlashMode() != null) {
                    parameters.setFlashMode(mFlashEnabled ? Parameters.FLASH_MODE_TORCH : Parameters.FLASH_MODE_OFF);
                }
                parameters.setRecordingHint(true);
                mCamera.setParameters(parameters);
                mCamera.setDisplayOrientation(mOrientation);

                try {
                    if (mMode == MODE_MEDIACODEC_API_2) {
                        mSurfaceView.startGLThread();
                        mCamera.setPreviewTexture(mSurfaceView.getSurfaceTexture());
                    } else {
                        mCamera.setPreviewDisplay(mSurfaceView.getHolder());
                    }
                } catch (IOException e) {
                    throw new InvalidSurfaceException("Invalid surface !");
                }

            } catch (RuntimeException e) {
                destroyCamera();
                throw e;
            }
        }
    }

    synchronized void destroyCamera() {
        if (mCamera != null) {
            if (mStreaming) super.stop();
            lockCamera();
            mCamera.stopPreview();
            try {
                mCamera.release();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage() != null ? e.getMessage() : "unknown error");
            }
            mCamera = null;
            mCameraLooper.quit();
            mUnlocked = false;
            mPreviewStarted = false;
        }
    }

    synchronized void updateCamera() throws RuntimeException {
        Log.v(TAG, "update camera");
        if (mPreviewStarted) {
            mPreviewStarted = false;
            mCamera.stopPreview();
        }

        Parameters parameters = mCamera.getParameters();
        mQuality = VideoQuality.determineClosestSupportedResolution(parameters, mQuality);
        int[] max = VideoQuality.determineMaximumSupportedFramerate(parameters);
        parameters.setPreviewFormat(mCameraImageFormat);
        parameters.setPreviewSize(mQuality.resX, mQuality.resY);
        parameters.setPreviewFpsRange(max[0], max[1]);

        try {
            mCamera.setParameters(parameters);
            mCamera.setDisplayOrientation(mOrientation);
            mCamera.startPreview();
            mPreviewStarted = true;
        } catch (RuntimeException e) {
            destroyCamera();
            throw e;
        }
    }

    void lockCamera() {
        if (mUnlocked) {
            Log.d(TAG, "Locking camera");
            try {
                mCamera.reconnect();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
            mUnlocked = false;
        }
    }

    void unlockCamera() {
        if (!mUnlocked) {
            Log.d(TAG, "Unlocking camera");
            try {
                mCamera.unlock();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
            mUnlocked = true;
        }
    }

    /**
     * Computes the average frame rate at which the preview callback is called.
     * We will then use this average framerate with the MediaCodec.
     * Blocks the thread in which this function is called.
     */
    private void measureFramerate() {
        final Semaphore lock = new Semaphore(0);

        final Camera.PreviewCallback callback = new Camera.PreviewCallback() {
            int i = 0, t = 0;
            long now, oldnow, count = 0;

            // TODO: guoshichao 如果我们使用的是ShareBuffer当中的数据的话，FrameRate是一个固定的值，
            // 这个值由我们自己来决定，不再需要动态的计算

            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                i++;
                now = System.nanoTime() / 1000;
                if (i > 3) {
                    t += now - oldnow;
                    count++;
                }
                if (i > 20) {
                    mQuality.framerate = (int) (1000000 / (t / count) + 1);
                    lock.release();
                }
                oldnow = now;
            }
        };

        mCamera.setPreviewCallback(callback);

        try {
            lock.tryAcquire(2, TimeUnit.SECONDS);
            Log.d(TAG, "Actual framerate: " + mQuality.framerate);
            if (mSettings != null) {
                Editor editor = mSettings.edit();
                editor.putInt(PREF_PREFIX + "fps" + mRequestedQuality.framerate + "," + mCameraImageFormat + "," + mRequestedQuality.resX + mRequestedQuality.resY, mQuality.framerate);
                editor.commit();
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted exception happened", e);
        }

        mCamera.setPreviewCallback(null);
    }

    /**
     * 循环发送消息的间隔:66ms
     */
    private static final int READ_MEDIA_STREAMING_DATA_INTERVAL = 66;

    private static final int MSG_READ_FRONT_STREAM_DATA = 1 << 1;
    private static final int MSG_READ_BACK_STREAM_DATA = 1 << 2;

    private static final byte[] FRONT_CAMERA_DATA_BUF = new byte[FRONT_CAMERA_FRAME_LEN];
    private static final byte[] BACK_CAMERA_DATA_BUF = new byte[BACK_CAMERA_FRAME_LEN];

    private Handler mFrontCameraReadingHandler;
    private Handler mBackCameraReadingHandler;

    private ByteBuffer[] mInputBuffers;

    private long mNow = System.nanoTime() / 1000, mOldnow = mNow, i = 0;

    private void processShareBufferData(boolean isFrontCamera) {
        if (mInputBuffers == null) {
            mInputBuffers = mMediaCodec.getInputBuffers();
        }
        try {
            final int readBytesCount;
            if (isFrontCamera) {
                readBytesCount = mFrontCameraMemFile.readBytes(FRONT_CAMERA_DATA_BUF, 1, 0, FRONT_CAMERA_FRAME_LEN);
                Log.d(TAG, "encode : FRONT CAMERA --> with bytes length of " + readBytesCount);
            } else {
                readBytesCount = mBackCameraMemFile.readBytes(BACK_CAMERA_DATA_BUF, 1, 0, BACK_CAMERA_FRAME_LEN);
                Log.d(TAG, "encode : BACK CAMERA --> with bytes length of " + readBytesCount);
            }
            // TODO: 然后对读取出的数据进行编码操作
            // TODO: 这里默认采用的是前置摄像头读取出的share buffer数据
            // TODO: 之后进行分别测试
            mOldnow = mNow;
            mNow = System.nanoTime() / 1000;
            if (i++ > 3) {
                i = 0;
            }
            try {
                int bufferIndex = mMediaCodec.dequeueInputBuffer(500000); // 500 seconds for timeout
                if (bufferIndex >= 0) {
                    mInputBuffers[bufferIndex].clear();
                    mConvertor.convert(FRONT_CAMERA_DATA_BUF, mInputBuffers[bufferIndex]);

                    mMediaCodec.queueInputBuffer(bufferIndex,
                            0,
                            mInputBuffers[bufferIndex].position(),
                            mNow,
                            0);
                } else {
                    Log.e(TAG, "Read from ShareBuffer --> no buffer available");
                }
            } finally {
                mCamera.addCallbackBuffer(FRONT_CAMERA_DATA_BUF);
            }

        } catch (IOException e) {
            Log.e(TAG, "IO exception happened while read data from Camera Memory File", e);
        } catch (IndexOutOfBoundsException e) {
            Log.e(TAG, "index out of bounds", e);
        }
    }

    private static class FrontCameraHandlerCallback implements Handler.Callback {
        private VideoStream outObj;

        FrontCameraHandlerCallback(WeakReference<VideoStream> outRef) {
            this.outObj = outRef.get();
        }

        @Override
        public boolean handleMessage(Message msg) {
            if (msg.what == MSG_READ_FRONT_STREAM_DATA) {
                // 首先将消息发送出去
                // 因为outObj.processShareBufferData();方法的执行会消耗时间
                // 所以需要先将消息发送出去，然后再处理数据
                outObj.mFrontCameraReadingHandler.sendEmptyMessageDelayed(MSG_READ_FRONT_STREAM_DATA, READ_MEDIA_STREAMING_DATA_INTERVAL);

                outObj.processShareBufferData(true);
            }
            return false;
        }
    }

    private static class BackCameraHandlerCallback implements Handler.Callback {
        private VideoStream outObj;

        BackCameraHandlerCallback(WeakReference<VideoStream> outRef) {
            this.outObj = outRef.get();
        }

        @Override
        public boolean handleMessage(Message msg) {
            if (msg.what == MSG_READ_BACK_STREAM_DATA) {
                // 直接循环发送相同的消息
                outObj.mBackCameraReadingHandler.sendEmptyMessageDelayed(MSG_READ_BACK_STREAM_DATA, READ_MEDIA_STREAMING_DATA_INTERVAL);

                outObj.processShareBufferData(false);
            }
            return false;
        }
    }
}
