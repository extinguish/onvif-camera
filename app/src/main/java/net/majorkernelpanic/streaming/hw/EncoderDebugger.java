/*
 * Copyright (C) 2011-2014 GUIGUI Simon, fyhertz@gmail.com
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

package net.majorkernelpanic.streaming.hw;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;

import net.majorkernelpanic.streaming.hw.CodecManager.Codec;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;

/**
 * The purpose of this class is to detect and by-pass some bugs (or underspecified configuration) that
 * encoders available through the MediaCodec API may have. <br />
 * Feeding the encoder with a surface is not tested here.
 * Some bugs you may have encountered:<br />
 * <ul>
 * <li>U and V panes reversed</li>
 * <li>Some padding is needed after the Y pane</li>
 * <li>stride!=width or slice-height!=height</li>
 * </ul>
 */
@SuppressLint("NewApi")
public class EncoderDebugger {

    public final static String TAG = "EncoderDebugger";

    /**
     * Prefix that will be used for all shared preferences saved by libstreaming.
     */
    private static final String PREF_PREFIX = "libstreaming-";

    /**
     * If this is set to false the test will be run only once and the result
     * will be saved in the shared preferences.
     */
    private static final boolean DEBUG = true;

    /**
     * Set this to true to see more logs.
     */
    private static final boolean VERBOSE = true;

    /**
     * Will be incremented every time this test is modified.
     */
    private static final int VERSION = 3;

    /**
     * Bitrate that will be used with the encoder.
     */
    private final static int BITRATE = 1000000;

    /**
     * Framerate that will be used to test the encoder.
     */
    private final static int FRAMERATE = 20;

    private final static String MIME_TYPE = "video/avc";

    private final static int NB_DECODED = 34;
    private final static int NB_ENCODED = 50;

    private int mDecoderColorFormat, mEncoderColorFormat;
    private String mDecoderName, mEncoderName, mErrorLog;
    private MediaCodec mEncoder, mDecoder;
    private int mWidth, mHeight, mSize;
    private byte[] mSPS, mPPS;
    private byte[] mData, mInitialImage;
    private MediaFormat mDecOutputFormat;
    private NV21Convertor mNV21;
    private SharedPreferences mPreferences;
    private byte[][] mVideo, mDecodedVideo;
    private String mB64PPS, mB64SPS;

    public synchronized static void asyncDebug(final Context context, final int width, final int height) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                    debug(prefs, width, height);
                } catch (Exception e) {
                    Log.e(TAG, "Exception happened while debug data", e);
                }
            }
        }).start();
    }

    public synchronized static EncoderDebugger debug(Context context, int width, int height) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return debug(prefs, width, height);
    }

    public synchronized static EncoderDebugger debug(SharedPreferences prefs, int width, int height) {
        EncoderDebugger debugger = new EncoderDebugger(prefs, width, height);
        debugger.debug();
        return debugger;
    }

    public String getB64PPS() {
        return mB64PPS;
    }

    public String getB64SPS() {
        return mB64SPS;
    }

    public String getEncoderName() {
        return mEncoderName;
    }

    public int getEncoderColorFormat() {
        return mEncoderColorFormat;
    }

    /**
     * This {@link NV21Convertor} will do the necessary work to feed properly the encoder.
     */
    public NV21Convertor getNV21Convertor() {
        return mNV21;
    }

    /**
     * A log of all the errors that occured during the test.
     */
    public String getErrorLog() {
        return mErrorLog;
    }

    private EncoderDebugger(SharedPreferences prefs, int width, int height) {
        mPreferences = prefs;
        mWidth = width;
        mHeight = height;
        mSize = width * height;
        reset();
    }

    private void reset() {
        mNV21 = new NV21Convertor();
        mVideo = new byte[NB_ENCODED][];
        mDecodedVideo = new byte[NB_DECODED][];
        mErrorLog = "";
        mPPS = null;
        mSPS = null;
    }

    private void debug() {
        // If testing the phone again is not needed,
        // we just restore the result from the shared preferences
        if (!checkTestNeeded()) {
            Log.d(TAG, "start check test ");
            String resolution = mWidth + "x" + mHeight + "-";

            boolean success = mPreferences.getBoolean(PREF_PREFIX + resolution + "success", false);
            if (!success) {
                throw new RuntimeException("Phone not supported with this resolution (" + mWidth + "x" + mHeight + ")");
            }

            mNV21.setSize(mWidth, mHeight);
            mNV21.setSliceHeigth(mPreferences.getInt(PREF_PREFIX + resolution + "sliceHeight", 0));
            mNV21.setStride(mPreferences.getInt(PREF_PREFIX + resolution + "stride", 0));
            mNV21.setYPadding(mPreferences.getInt(PREF_PREFIX + resolution + "padding", 0));
            mNV21.setPlanar(mPreferences.getBoolean(PREF_PREFIX + resolution + "planar", false));
            mNV21.setColorPanesReversed(mPreferences.getBoolean(PREF_PREFIX + resolution + "reversed", false));
            mEncoderName = mPreferences.getString(PREF_PREFIX + resolution + "encoderName", "");
            mEncoderColorFormat = mPreferences.getInt(PREF_PREFIX + resolution + "colorFormat", 0);
            mB64PPS = mPreferences.getString(PREF_PREFIX + resolution + "pps", "");
            mB64SPS = mPreferences.getString(PREF_PREFIX + resolution + "sps", "");

            return;
        }

        if (VERBOSE) {
            Log.d(TAG, ">>>> Testing the phone for resolution " + mWidth + "x" + mHeight);
        }

        // Builds a list of available encoders and decoders we may be able to use
        // because they support some nice color formats
        Codec[] encoders = CodecManager.findEncodersForMimeType(MIME_TYPE);
        Codec[] decoders = CodecManager.findDecodersForMimeType(MIME_TYPE);

        int count = 0, n = 1;
        for (Codec encoder1 : encoders) {
            count += encoder1.formats.length;
        }

        // Tries available encoders
        for (Codec encoder : encoders) {
            for (int j = 0; j < encoder.formats.length; j++) {
                reset();

                mEncoderName = encoder.name;
                mEncoderColorFormat = encoder.formats[j];
                Log.d(TAG, "testing with encoder of " + encoder.name + ", with format of " + mEncoderColorFormat);
                if (VERBOSE)
                    Log.v(TAG, ">> Test " + (n++) + "/" + count + ": " + mEncoderName + " with color format " + mEncoderColorFormat + " at " + mWidth + "x" + mHeight);

                // Converts from NV21 to YUV420 with the specified parameters
                mNV21.setSize(mWidth, mHeight);
                mNV21.setSliceHeigth(mHeight);
                mNV21.setStride(mWidth);
                mNV21.setYPadding(0);
                mNV21.setEncoderColorFormat(mEncoderColorFormat);

                // /!\ NV21Convertor can directly modify the input
                createTestImage();
                mData = mNV21.convert(mInitialImage);

                try {

                    // Starts the encoder
                    configureEncoder();
                    searchSPSandPPS();

                    if (VERBOSE) {
                        Log.v(TAG, "SPS and PPS in b64: SPS=" + mB64SPS + ", PPS=" + mB64PPS);
                    }

                    // Feeds the encoder with an image repeatidly to produce some NAL units
                    encode();

                    // We now try to decode the NALs with decoders available on the phone
                    boolean decoded = false;
                    for (int k = 0; k < decoders.length && !decoded; k++) {
                        for (int l = 0; l < decoders[k].formats.length && !decoded; l++) {
                            mDecoderName = decoders[k].name;
                            mDecoderColorFormat = decoders[k].formats[l];
                            try {
                                configureDecoder();
                            } catch (final Exception e) {
                                if (VERBOSE)
                                    Log.d(TAG, mDecoderName + " can't be used with " + mDecoderColorFormat + " at " + mWidth + "x" + mHeight);
                                releaseDecoder();
                                break;
                            }
                            try {
                                decode(true);
                                if (VERBOSE) {
                                    Log.d(TAG, mDecoderName + " successfully decoded the NALs (color format " + mDecoderColorFormat + ")");
                                }
                                decoded = true;
                            } catch (Exception e) {
                                if (VERBOSE)
                                    Log.e(TAG, mDecoderName + " failed to decode the NALs");
                                e.printStackTrace();
                            } finally {
                                releaseDecoder();
                            }
                        }
                    }

                    if (!decoded)
                        throw new RuntimeException("Failed to decode NALs from the encoder.");

                    // Compares the image before and after
                    if (!compareLumaPanes()) {
                        // TODO: try again with a different stride
                        // TODO: try again with the "stride" param
                        throw new RuntimeException("It is likely that stride!=width");
                    }

                    int padding;
                    if ((padding = checkPaddingNeeded()) > 0) {
                        if (padding < 4096) {
                            if (VERBOSE) Log.d(TAG, "Some padding is needed: " + padding);
                            mNV21.setYPadding(padding);
                            createTestImage();
                            mData = mNV21.convert(mInitialImage);
                            encodeDecode();
                        } else {
                            // TODO: try again with a different sliceHeight
                            // TODO: try again with the "slice-height" param
                            throw new RuntimeException("It is likely that sliceHeight!=height");
                        }
                    }

                    createTestImage();
                    if (!compareChromaPanes(false)) {
                        if (compareChromaPanes(true)) {
                            mNV21.setColorPanesReversed(true);
                            if (VERBOSE) Log.d(TAG, "U and V pane are reversed");
                        } else {
                            throw new RuntimeException("Incorrect U or V pane...");
                        }
                    }

                    saveTestResult(true);
                    Log.v(TAG, "The encoder " + mEncoderName + " is usable with resolution " + mWidth + "x" + mHeight);
                    return;

                } catch (final Exception e) {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    e.printStackTrace(pw);
                    String stack = sw.toString();
                    String str = "Encoder " + mEncoderName + " cannot be used with color format " + mEncoderColorFormat;
                    if (VERBOSE) {
                        Log.e(TAG, str, e);
                    }
                    mErrorLog += str + "\n" + stack;
                    e.printStackTrace();
                } finally {
                    Log.e(TAG, "release the encoder");
                    releaseEncoder();
                }
            }
        }

        saveTestResult(false);
        Log.e(TAG, "No usable encoder were found on the phone for resolution " + mWidth + "x" + mHeight);
        throw new RuntimeException("No usable encoder were found on the phone for resolution " + mWidth + "x" + mHeight);
    }

    private boolean checkTestNeeded() {
        String resolution = mWidth + "x" + mHeight + "-";

        // Forces the test
        if (DEBUG || mPreferences == null) return true;

        // If the sdk has changed on the phone, or the version of the test
        // it has to be run again
        if (mPreferences.contains(PREF_PREFIX + resolution + "lastSdk")) {
            int lastSdk = mPreferences.getInt(PREF_PREFIX + resolution + "lastSdk", 0);
            int lastVersion = mPreferences.getInt(PREF_PREFIX + resolution + "lastVersion", 0);
            return Build.VERSION.SDK_INT > lastSdk || VERSION > lastVersion;
        } else {
            return true;
        }
    }

    /**
     * Saves the result of the test in the shared preferences,
     * we will run it again only if the SDK has changed on the phone,
     * or if this test has been modified.
     */
    private void saveTestResult(boolean success) {
        String resolution = mWidth + "x" + mHeight + "-";
        Editor editor = mPreferences.edit();

        editor.putBoolean(PREF_PREFIX + resolution + "success", success);

        if (success) {
            editor.putInt(PREF_PREFIX + resolution + "lastSdk", Build.VERSION.SDK_INT);
            editor.putInt(PREF_PREFIX + resolution + "lastVersion", VERSION);
            editor.putInt(PREF_PREFIX + resolution + "sliceHeight", mNV21.getSliceHeight());
            editor.putInt(PREF_PREFIX + resolution + "stride", mNV21.getStride());
            editor.putInt(PREF_PREFIX + resolution + "padding", mNV21.getYPadding());
            editor.putBoolean(PREF_PREFIX + resolution + "planar", mNV21.getPlanar());
            editor.putBoolean(PREF_PREFIX + resolution + "reversed", mNV21.getUVPanesReversed());
            editor.putString(PREF_PREFIX + resolution + "encoderName", mEncoderName);
            editor.putInt(PREF_PREFIX + resolution + "colorFormat", mEncoderColorFormat);
            editor.putString(PREF_PREFIX + resolution + "encoderName", mEncoderName);
            editor.putString(PREF_PREFIX + resolution + "pps", mB64PPS);
            editor.putString(PREF_PREFIX + resolution + "sps", mB64SPS);
        }

        editor.commit();
    }

    /**
     * Creates the test image that will be used to feed the encoder.
     */
    private void createTestImage() {
        mInitialImage = new byte[3 * mSize / 2];
        for (int i = 0; i < mSize; i++) {
            mInitialImage[i] = (byte) (40 + i % 199);
        }
        for (int i = mSize; i < 3 * mSize / 2; i += 2) {
            mInitialImage[i] = (byte) (40 + i % 200);
            mInitialImage[i + 1] = (byte) (40 + (i + 99) % 200);
        }
    }

    public static byte[] createTestImg() {
        final int size = 320 * 240;
        byte[] testImg = new byte[3 * size / 2];
        for (int i = 0; i < size; i++) {
            testImg[i] = (byte) (40 + i % 199);
        }
        for (int i = size; i < 3 * size / 2; i += 2) {
            testImg[i] = (byte) (40 + i % 200);
            testImg[i + 1] = (byte) (40 + (i + 99) % 200);
        }
        return testImg;
    }

    /**
     * Compares the Y pane of the initial image, and the Y pane
     * after having encoded & decoded the image.
     */
    private boolean compareLumaPanes() {
        int d, e, f = 0;
        for (int j = 0; j < NB_DECODED; j++) {
            for (int i = 0; i < mSize; i += 10) {
                d = (mInitialImage[i] & 0xFF) - (mDecodedVideo[j][i] & 0xFF);
                e = (mInitialImage[i + 1] & 0xFF) - (mDecodedVideo[j][i + 1] & 0xFF);
                d = d < 0 ? -d : d;
                e = e < 0 ? -e : e;
                if (d > 50 && e > 50) {
                    mDecodedVideo[j] = null;
                    f++;
                    break;
                }
            }
        }
        return f <= NB_DECODED / 2;
    }

    private int checkPaddingNeeded() {
        int i = 0, j = 3 * mSize / 2 - 1, max = 0;
        int[] r = new int[NB_DECODED];
        for (int k = 0; k < NB_DECODED; k++) {
            if (mDecodedVideo[k] != null) {
                i = 0;
                while (i < j && (mDecodedVideo[k][j - i] & 0xFF) < 50) i += 2;
                if (i > 0) {
                    r[k] = ((i >> 6) << 6);
                    max = r[k] > max ? r[k] : max;
                    if (VERBOSE) Log.e(TAG, "Padding needed: " + r[k]);
                } else {
                    if (VERBOSE) Log.v(TAG, "No padding needed.");
                }
            }
        }

        return ((max >> 6) << 6);
    }

    /**
     * Compares the U or V pane of the initial image, and the U or V pane
     * after having encoded & decoded the image.
     */
    private boolean compareChromaPanes(boolean crossed) {
        int d, f = 0;

        for (int j = 0; j < NB_DECODED; j++) {
            if (mDecodedVideo[j] != null) {
                // We compare the U and V pane before and after
                if (!crossed) {
                    for (int i = mSize; i < 3 * mSize / 2; i += 1) {
                        d = (mInitialImage[i] & 0xFF) - (mDecodedVideo[j][i] & 0xFF);
                        d = d < 0 ? -d : d;
                        if (d > 50) {
                            //if (VERBOSE) Log.e(TAG,"BUG "+(i-mSize)+" d "+d);
                            f++;
                            break;
                        }
                    }

                    // We compare the V pane before with the U pane after
                } else {
                    for (int i = mSize; i < 3 * mSize / 2; i += 2) {
                        d = (mInitialImage[i] & 0xFF) - (mDecodedVideo[j][i + 1] & 0xFF);
                        d = d < 0 ? -d : d;
                        if (d > 50) {
                            f++;
                        }
                    }
                }
            }
        }
        return f <= NB_DECODED / 2;
    }

    /**
     * Converts the image obtained from the decoder to NV21.
     */
    private void convertToNV21(int k) {
        byte[] buffer = new byte[3 * mSize / 2];

        int stride = mWidth, sliceHeight = mHeight;
        int colorFormat = mDecoderColorFormat;
        boolean planar = false;

        if (mDecOutputFormat != null) {
            MediaFormat format = mDecOutputFormat;
            if (format != null) {
                if (format.containsKey("slice-height")) {
                    sliceHeight = format.getInteger("slice-height");
                    if (sliceHeight < mHeight) sliceHeight = mHeight;
                }
                if (format.containsKey("stride")) {
                    stride = format.getInteger("stride");
                    if (stride < mWidth) stride = mWidth;
                }
                if (format.containsKey(MediaFormat.KEY_COLOR_FORMAT)) {
                    if (format.getInteger(MediaFormat.KEY_COLOR_FORMAT) > 0) {
                        colorFormat = format.getInteger(MediaFormat.KEY_COLOR_FORMAT);
                    }
                }
            }
        }

        switch (colorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                planar = false;
                break;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
                planar = true;
                break;
        }

        for (int i = 0; i < mSize; i++) {
            if (i % mWidth == 0) i += stride - mWidth;
            buffer[i] = mDecodedVideo[k][i];
        }

        if (!planar) {
            for (int i = 0, j = 0; j < mSize / 4; i += 1, j += 1) {
                if (i % mWidth / 2 == 0) i += (stride - mWidth) / 2;
                buffer[mSize + 2 * j + 1] = mDecodedVideo[k][stride * sliceHeight + 2 * i];
                buffer[mSize + 2 * j] = mDecodedVideo[k][stride * sliceHeight + 2 * i + 1];
            }
        } else {
            for (int i = 0, j = 0; j < mSize / 4; i += 1, j += 1) {
                if (i % mWidth / 2 == 0) i += (stride - mWidth) / 2;
                buffer[mSize + 2 * j + 1] = mDecodedVideo[k][stride * sliceHeight + i];
                buffer[mSize + 2 * j] = mDecodedVideo[k][stride * sliceHeight * 5 / 4 + i];
            }
        }
        mDecodedVideo[k] = buffer;
    }

    /**
     * Instantiates and starts the encoder.
     */
    private void configureEncoder() throws IOException {
        mEncoder = MediaCodec.createByCodecName(mEncoderName);
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, BITRATE);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAMERATE);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, mEncoderColorFormat);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        mEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mEncoder.start();
    }

    private void releaseEncoder() {
        if (mEncoder != null) {
            try {
                mEncoder.stop();
            } catch (final Exception e) {
                Log.e(TAG, "Exception happened while stop the encoder", e);
            }
            try {
                mEncoder.release();
            } catch (final Exception e) {
                Log.e(TAG, "Exception happened while release the encoder", e);
            }
        }
    }

    /**
     * Instantiates and starts the decoder.
     */
    private void configureDecoder() throws IOException {
        byte[] prefix = new byte[]{0x00, 0x00, 0x00, 0x01};

        ByteBuffer csd0 = ByteBuffer.allocate(4 + mSPS.length + 4 + mPPS.length);
        csd0.put(new byte[]{0x00, 0x00, 0x00, 0x01});
        csd0.put(mSPS);
        csd0.put(new byte[]{0x00, 0x00, 0x00, 0x01});
        csd0.put(mPPS);

        mDecoder = MediaCodec.createByCodecName(mDecoderName);
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
        mediaFormat.setByteBuffer("csd-0", csd0);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, mDecoderColorFormat);
        mDecoder.configure(mediaFormat, null, null, 0);
        mDecoder.start();

        ByteBuffer[] decInputBuffers = mDecoder.getInputBuffers();

        int decInputIndex = mDecoder.dequeueInputBuffer(1000000 / FRAMERATE);
        if (decInputIndex >= 0) {
            decInputBuffers[decInputIndex].clear();
            decInputBuffers[decInputIndex].put(prefix);
            decInputBuffers[decInputIndex].put(mSPS);
            mDecoder.queueInputBuffer(decInputIndex, 0, decInputBuffers[decInputIndex].position(), timestamp(), 0);
        } else {
            if (VERBOSE) Log.e(TAG, "No buffer available !");
        }

        decInputIndex = mDecoder.dequeueInputBuffer(1000000 / FRAMERATE);
        if (decInputIndex >= 0) {
            decInputBuffers[decInputIndex].clear();
            decInputBuffers[decInputIndex].put(prefix);
            decInputBuffers[decInputIndex].put(mPPS);
            mDecoder.queueInputBuffer(decInputIndex, 0, decInputBuffers[decInputIndex].position(), timestamp(), 0);
        } else {
            if (VERBOSE) Log.e(TAG, "No buffer available !");
        }


    }

    private void releaseDecoder() {
        if (mDecoder != null) {
            try {
                mDecoder.stop();
            } catch (Exception e) {
                Log.e(TAG, "fail to stop the decoder");
            }
            try {
                mDecoder.release();
            } catch (Exception e) {
                Log.e(TAG, "fail to release the decoder");
            }
        }
    }

    /**
     * 搜索ShareBuffer视频流当中的sps和pps
     */
    public static Pair<String, String> searchSPSandPPSForShareBuffer() {
        Log.d(TAG, "--------------> search sps and pps of ShareBuffer");
        try {
            NV21Convertor convertor = NV21Convertor.getDefaultNV21Convertor();
            byte[] testImg = createTestImg();
            byte[] data = convertor.convert(testImg);
            byte[] sps = null, pps = null;
            MediaCodec encoder = MediaCodec.createEncoderByType(MIME_TYPE);

            MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, 320, 240);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, BITRATE);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAMERATE);
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            encoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();

            Log.d(TAG, "create MediaCodec instance ");
            ByteBuffer[] inputBuffers = encoder.getInputBuffers();
            ByteBuffer[] outputBuffers = encoder.getOutputBuffers();
            Log.d(TAG, "get input buffers and output buffers");
            BufferInfo info = new BufferInfo();
            byte[] csd = new byte[128];
            int len = 0, p = 4, q = 4;

            long elapsed = 0, now = System.nanoTime() / 1000;
            Log.d(TAG, "start fulling data");
            while (elapsed < 3000000 && (sps == null || pps == null)) {
                // Some encoders won't give us the SPS and PPS unless they receive something to encode first...
                int bufferIndex = encoder.dequeueInputBuffer(1000000 / 15);
                if (bufferIndex >= 0) {
                    if (inputBuffers[bufferIndex].capacity() < data.length) {
                        Log.d(TAG, "the input buffer capacity is invalid");
                        throw new RuntimeException("The input buffer is not big enough.");
                    }
                    inputBuffers[bufferIndex].clear();
                    inputBuffers[bufferIndex].put(data, 0, data.length);
                    encoder.queueInputBuffer(bufferIndex, 0, data.length, System.nanoTime() / 1000,
                            0);
                } else {
                    if (VERBOSE) {
                        Log.e(TAG, "No buffer available !");
                    }
                }

                Log.d(TAG, "now looking for SPS and PPS");
                // We are looking for the SPS and the PPS here. As always, Android is very inconsistent, I have observed that some
                // encoders will give those parameters through the MediaFormat object (that is the normal behaviour).
                // But some other will not, in that case we try to find a NAL unit of type 7 or 8 in the byte stream outputed by the encoder...

                int index = encoder.dequeueOutputBuffer(info, 1000000 / 15);

                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    Log.d(TAG, "INFO OUTPUT FORMAT CHANGED");
                    // The PPS and PPS should be there
                    MediaFormat format = encoder.getOutputFormat();
                    ByteBuffer spsb = format.getByteBuffer("csd-0");
                    ByteBuffer ppsb = format.getByteBuffer("csd-1");
                    sps = new byte[spsb.capacity() - 4];
                    spsb.position(4);
                    spsb.get(sps, 0, sps.length);
                    pps = new byte[ppsb.capacity() - 4];
                    ppsb.position(4);
                    ppsb.get(pps, 0, pps.length);
                    break;
                } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    Log.d(TAG, "INFO OUTPUT BUFFER CHANGED ");
                    outputBuffers = encoder.getOutputBuffers();
                } else if (index >= 0) {
                    Log.d(TAG, "index normal ");
                    len = info.size;
                    if (len < 128) {
                        outputBuffers[index].get(csd, 0, len);
                        if (len > 0 && csd[0] == 0 && csd[1] == 0 && csd[2] == 0 && csd[3] == 1) {
                            // Parses the SPS and PPS, they could be in two different packets and in a different order
                            //depending on the phone so we don't make any assumption about that
                            while (p < len) {
                                while (!(csd[p + 0] == 0 && csd[p + 1] == 0 && csd[p + 2] == 0 && csd[p + 3] == 1) && p + 3 < len)
                                    p++;
                                if (p + 3 >= len) p = len;
                                if ((csd[q] & 0x1F) == 7) {
                                    sps = new byte[p - q];
                                    System.arraycopy(csd, q, sps, 0, p - q);
                                } else {
                                    pps = new byte[p - q];
                                    System.arraycopy(csd, q, pps, 0, p - q);
                                }
                                p += 4;
                                q = p;
                            }
                        }
                    }
                    encoder.releaseOutputBuffer(index, false);
                }
                elapsed = System.nanoTime() / 1000 - now;
            }

            if (sps == null || pps == null) {
                throw new RuntimeException("Could not determine the SPS & PPS.");
            }
            String base64PPS = Base64.encodeToString(pps, 0, pps.length, Base64.NO_WRAP);
            String base64SPS = Base64.encodeToString(sps, 0, sps.length, Base64.NO_WRAP);
            Log.d(TAG, "----------------> the finally pps we get for ShareBuffer are " + base64PPS + ", and sps we get are " + base64SPS);
            return new Pair<>(base64PPS, base64SPS);
        } catch (IOException e) {
            Log.d(TAG, "Exception happened ", e);
        }
        return null;
    }

    /**
     * Tries to obtain the SPS and the PPS for the encoder.
     */
    private long searchSPSandPPS() {
        Log.d(TAG, "---------------> search sps and pps");
        ByteBuffer[] inputBuffers = mEncoder.getInputBuffers();
        ByteBuffer[] outputBuffers = mEncoder.getOutputBuffers();
        BufferInfo info = new BufferInfo();
        byte[] csd = new byte[128];
        int len = 0, p = 4, q = 4;
        long elapsed = 0, now = timestamp();

        while (elapsed < 3000000 && (mSPS == null || mPPS == null)) {

            // Some encoders won't give us the SPS and PPS unless they receive something to encode first...
            int bufferIndex = mEncoder.dequeueInputBuffer(1000000 / FRAMERATE);
            if (bufferIndex >= 0) {
                check(inputBuffers[bufferIndex].capacity() >= mData.length, "The input buffer is not big enough.");
                inputBuffers[bufferIndex].clear();
                inputBuffers[bufferIndex].put(mData, 0, mData.length);
                mEncoder.queueInputBuffer(bufferIndex, 0, mData.length, timestamp(), 0);
            } else {
                if (VERBOSE) {
                    Log.e(TAG, "No buffer available !");
                }
            }

            // We are looking for the SPS and the PPS here. As always, Android is very inconsistent, I have observed that some
            // encoders will give those parameters through the MediaFormat object (that is the normal behaviour).
            // But some other will not, in that case we try to find a NAL unit of type 7 or 8 in the byte stream outputed by the encoder...

            int index = mEncoder.dequeueOutputBuffer(info, 1000000 / FRAMERATE);

            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {

                // The PPS and PPS should be there
                MediaFormat format = mEncoder.getOutputFormat();
                ByteBuffer spsb = format.getByteBuffer("csd-0");
                ByteBuffer ppsb = format.getByteBuffer("csd-1");
                mSPS = new byte[spsb.capacity() - 4];
                spsb.position(4);
                spsb.get(mSPS, 0, mSPS.length);
                mPPS = new byte[ppsb.capacity() - 4];
                ppsb.position(4);
                ppsb.get(mPPS, 0, mPPS.length);
                break;

            } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputBuffers = mEncoder.getOutputBuffers();
            } else if (index >= 0) {

                len = info.size;
                if (len < 128) {
                    outputBuffers[index].get(csd, 0, len);
                    if (len > 0 && csd[0] == 0 && csd[1] == 0 && csd[2] == 0 && csd[3] == 1) {
                        // Parses the SPS and PPS, they could be in two different packets and in a different order
                        //depending on the phone so we don't make any assumption about that
                        while (p < len) {
                            while (!(csd[p + 0] == 0 && csd[p + 1] == 0 && csd[p + 2] == 0 && csd[p + 3] == 1) && p + 3 < len)
                                p++;
                            if (p + 3 >= len) p = len;
                            if ((csd[q] & 0x1F) == 7) {
                                mSPS = new byte[p - q];
                                System.arraycopy(csd, q, mSPS, 0, p - q);
                            } else {
                                mPPS = new byte[p - q];
                                System.arraycopy(csd, q, mPPS, 0, p - q);
                            }
                            p += 4;
                            q = p;
                        }
                    }
                }
                mEncoder.releaseOutputBuffer(index, false);
            }

            elapsed = timestamp() - now;
        }

        check(mPPS != null & mSPS != null, "Could not determine the SPS & PPS.");
        mB64PPS = Base64.encodeToString(mPPS, 0, mPPS.length, Base64.NO_WRAP);
        mB64SPS = Base64.encodeToString(mSPS, 0, mSPS.length, Base64.NO_WRAP);

        Log.d(TAG, "the finally pps we get are " + mB64PPS + ", and sps we get are " + mB64SPS);
        return elapsed;
    }

    /**
     * @param withPrefix If set to true, the decoder will be fed with NALs preceeded with 0x00000001.
     * @return How long it took to decode all the NALs
     */
    private long decode(boolean withPrefix) {
        Log.d(TAG, "start decode with prefix of " + withPrefix);
        int n = 0, i = 0, j = 0;
        long elapsed = 0, now = timestamp();
        int decInputIndex = 0, decOutputIndex = 0;
        ByteBuffer[] decInputBuffers = mDecoder.getInputBuffers();
        ByteBuffer[] decOutputBuffers = mDecoder.getOutputBuffers();
        BufferInfo info = new BufferInfo();

        while (elapsed < 3000000) {
            // Feeds the decoder with a NAL unit
            Log.d(TAG, "feeds the decoder with NAL unit");
            if (i < NB_ENCODED) {
                decInputIndex = mDecoder.dequeueInputBuffer(1000000 / FRAMERATE);
                if (decInputIndex >= 0) {
                    int l1 = decInputBuffers[decInputIndex].capacity();
                    int l2 = mVideo[i].length;
                    decInputBuffers[decInputIndex].clear();

                    if ((withPrefix && hasPrefix(mVideo[i])) || (!withPrefix && !hasPrefix(mVideo[i]))) {
                        check(l1 >= l2, "The decoder input buffer is not big enough (nal=" + l2 + ", capacity=" + l1 + ").");
                        decInputBuffers[decInputIndex].put(mVideo[i], 0, mVideo[i].length);
                    } else if (withPrefix && !hasPrefix(mVideo[i])) {
                        check(l1 >= l2 + 4, "The decoder input buffer is not big enough (nal=" + (l2 + 4) + ", capacity=" + l1 + ").");
                        decInputBuffers[decInputIndex].put(new byte[]{0, 0, 0, 1});
                        decInputBuffers[decInputIndex].put(mVideo[i], 0, mVideo[i].length);
                    } else if (!withPrefix && hasPrefix(mVideo[i])) {
                        check(l1 >= l2 - 4, "The decoder input buffer is not big enough (nal=" + (l2 - 4) + ", capacity=" + l1 + ").");
                        decInputBuffers[decInputIndex].put(mVideo[i], 4, mVideo[i].length - 4);
                    }

                    mDecoder.queueInputBuffer(decInputIndex, 0, l2, timestamp(), 0);
                    i++;
                } else {
                    if (VERBOSE) {
                        Log.d(TAG, "No buffer available !");
                    }
                }
            }

            Log.d(TAG, "tries to get a decoded image");
            // Tries to get a decoded image
            decOutputIndex = mDecoder.dequeueOutputBuffer(info, 1000000 / FRAMERATE);
            if (decOutputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                Log.d(TAG, "decode --> output buffer changed");
                decOutputBuffers = mDecoder.getOutputBuffers();
            } else if (decOutputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.d(TAG, "decode --> output format changed");
                mDecOutputFormat = mDecoder.getOutputFormat();
            } else if (decOutputIndex >= 0) {
                Log.d(TAG, "decode output index are " + decOutputIndex);
                if (n > 2) {
                    Log.d(TAG, "successful in encode and decode an image");
                    // We have successfully encoded and decoded an image !
                    int length = info.size;
                    mDecodedVideo[j] = new byte[length];
                    decOutputBuffers[decOutputIndex].clear();
                    decOutputBuffers[decOutputIndex].get(mDecodedVideo[j], 0, length);
                    // Converts the decoded frame to NV21
                    convertToNV21(j);
                    if (j >= NB_DECODED - 1) {
                        Log.d(TAG, "encode --> flush media codec");
                        flushMediaCodec(mDecoder);
                        if (VERBOSE) {
                            Log.v(TAG, "Decoding " + n + " frames took " + elapsed / 1000 + " ms");
                        }
                        return elapsed;
                    }
                    j++;
                }
                mDecoder.releaseOutputBuffer(decOutputIndex, false);
                n++;
            }
            elapsed = timestamp() - now;
        }
        throw new RuntimeException("The decoder did not decode anything.");
    }

    private long encode() {
        int n = 0;
        long elapsed = 0, now = timestamp();
        int encOutputIndex = 0, encInputIndex = 0;
        BufferInfo info = new BufferInfo();
        ByteBuffer[] encInputBuffers = mEncoder.getInputBuffers();
        ByteBuffer[] encOutputBuffers = mEncoder.getOutputBuffers();

        while (elapsed < 5000000) {
            Log.d(TAG, "feeds the encoder with an image");
            // Feeds the encoder with an image
            encInputIndex = mEncoder.dequeueInputBuffer(1000000 / FRAMERATE);
            if (encInputIndex >= 0) {
                check(encInputBuffers[encInputIndex].capacity() >= mData.length, "The input buffer is not big enough.");
                encInputBuffers[encInputIndex].clear();
                encInputBuffers[encInputIndex].put(mData, 0, mData.length);
                mEncoder.queueInputBuffer(encInputIndex, 0, mData.length, timestamp(), 0);
            } else {
                if (VERBOSE) {
                    Log.d(TAG, "No buffer available !");
                }
            }

            Log.d(TAG, "tries to get a NAL unit");
            // Tries to get a NAL unit
            encOutputIndex = mEncoder.dequeueOutputBuffer(info, 1000000 / FRAMERATE);
            if (encOutputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                encOutputBuffers = mEncoder.getOutputBuffers();
            } else if (encOutputIndex >= 0) {
                mVideo[n] = new byte[info.size];
                encOutputBuffers[encOutputIndex].clear();
                encOutputBuffers[encOutputIndex].get(mVideo[n++], 0, info.size);
                mEncoder.releaseOutputBuffer(encOutputIndex, false);
                if (n >= NB_ENCODED) {
                    Log.d(TAG, "encode --> flush MediaCodec");
                    flushMediaCodec(mEncoder);
                    return elapsed;
                }
            }

            elapsed = timestamp() - now;
        }

        throw new RuntimeException("The encoder is too slow.");
    }

    /**
     * Makes sure the NAL has a header or not.
     * <p>
     * withPrefix If set to true, the NAL will be preceded with 0x00000001.
     */
    private boolean hasPrefix(byte[] nal) {
        return nal[0] == 0 && nal[1] == 0 && nal[2] == 0 && nal[3] == 0x01;
    }

    private void encodeDecode() {
        encode();
        try {
            configureDecoder();
            decode(true);
        } catch (IOException e) {
            Log.e(TAG, "Exception happened while configure the Decoder", e);
        } finally {
            releaseDecoder();
        }
    }

    private void flushMediaCodec(MediaCodec mc) {
        int index = 0;
        BufferInfo info = new BufferInfo();
        while (index != MediaCodec.INFO_TRY_AGAIN_LATER) {
            index = mc.dequeueOutputBuffer(info, 1000000 / FRAMERATE);
            if (index >= 0) {
                mc.releaseOutputBuffer(index, false);
            }
        }
    }

    private void check(boolean cond, String message) {
        if (!cond) {
            if (VERBOSE) Log.e(TAG, message);
            throw new IllegalStateException(message);
        }
    }

    private long timestamp() {
        return System.nanoTime() / 1000;
    }

}
