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

package net.majorkernelpanic.streaming.rtp;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.ByteBuffer;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.Log;

import net.majorkernelpanic.spydroid.SpydroidApplication;

import org.apache.http.util.EntityUtils;
import org.spongycastle.crypto.tls.TlsAgreementCredentials;

/**
 * An InputStream that uses data from a MediaCodec.
 * The purpose of this class is to interface existing RTP packetizers of
 * libstreaming with the new MediaCodec API. This class is not thread safe !
 * <p>
 * 从{@link MediaCodec}当中读取数据出来.
 */
@SuppressLint("NewApi")
public class MediaCodecInputStream extends InputStream {
    public static final String TAG = "MediaCodecInputStream";

    private MediaCodec mMediaCodec;
    private BufferInfo mBufferInfo = new BufferInfo();
    private ByteBuffer[] mBuffers;
    private ByteBuffer mBuffer = null;
    private int mIndex = -1;
    /**
     * 用于标识当前的video stream已经到达了末端
     * 我们通过该值来通知停止从MediaCodec当中读取数据
     */
    private boolean mClosed = false;

    public MediaFormat mMediaFormat;

    private static final boolean TEST_ENCODE = true;

    public MediaCodecInputStream(MediaCodec mediaCodec) {
        mMediaCodec = mediaCodec;
        // mBuffers = mMediaCodec.getOutputBuffers();
    }

    @Override
    public void close() {
        mClosed = true;
    }

    @Override
    public int read() throws IOException {
        return 0;
    }

    private MediaCodec.BufferInfo mMediaBufferInfo = new MediaCodec.BufferInfo();
    private static final int TIMEOUT_MS = 500000;

    @SuppressLint("WrongConstant")
    @Override
    public int read(@NonNull byte[] buffer, int offset, int length) throws IOException {
        if (SpydroidApplication.USE_SHARE_BUFFER_DATA) {
            int min = 0;
            ByteBuffer[] encoderOutputBuffers = mMediaCodec.getOutputBuffers();
            if (mClosed) {
                mMediaCodec.signalEndOfInputStream();
            }
            for (; ; ) {
                int encoderStatus = mMediaCodec.dequeueOutputBuffer(mMediaBufferInfo, TIMEOUT_MS);
                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (mClosed) {
                        Log.d(TAG, "break loop, wait for next loop while data available");
                        break;
                    } else {
                        Log.d(TAG, "no output available, just wait, until preset timeout reached");
                    }
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // not expected for an encoder
                    encoderOutputBuffers = mMediaCodec.getOutputBuffers();
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // 格式发生了变化,我们暂时不处理这种情况
                    Log.w(TAG, "encode output buffer has changed, and do not handle this case");
                } else if (encoderStatus < 0) {
                    Log.d(TAG, "unexpected result from encoder.dequeueOutputBuffer");
                } else {
                    ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                    if (encodedData == null) {
                        Log.e(TAG, "encode wrong");
                        break;
                    }

                    if ((mMediaBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        Log.d(TAG, "ignore BUFFER_FLAG_CODEC_CONFIG");
                        mMediaBufferInfo.size = 0;
                    }

                    if (mMediaBufferInfo.size != 0) {
                        encodedData.position(mMediaBufferInfo.offset);
                        encodedData.limit(mMediaBufferInfo.offset + mMediaBufferInfo.size);
                        // 将encoded当中的数据填充到buffer当中
                        min = length < mMediaBufferInfo.size - encodedData.position() ? length : mMediaBufferInfo.size - encodedData.position();
                        encodedData.get(buffer, offset, min);
                    }

                    mMediaCodec.releaseOutputBuffer(encoderStatus, false);

                    if ((mMediaBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        if (!mClosed) {
                            Log.w(TAG, "reach the end of stream unexpected????");
                        } else {
                            Log.d(TAG, "end of stream");
                        }
                        // 等待下一次编码数据可行
                        break;
                    }

                    // 将buffer写入到本地
                    if (TEST_ENCODE) {
                        Log.d(TAG, "write buffer with length of " + buffer.length);
                        writeDataToLocal(buffer);
                    }
                }
            }
            return min;
        } else {
            int min = 0;
            try {
                if (mBuffer == null) {
                    while (!Thread.interrupted() && !mClosed) {
                        mBuffers = mMediaCodec.getOutputBuffers();

                        mIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_MS);
                        if (mIndex >= 0) {
                            //Log.d(TAG,"Index: "+mIndex+" Time: "+mBufferInfo.presentationTimeUs+" size: "+mBufferInfo.size);
                            mBuffer = mBuffers[mIndex];
                            mBuffer.position(0);
                            break;
                        } else if (mIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                            mBuffers = mMediaCodec.getOutputBuffers();
                        } else if (mIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            mMediaFormat = mMediaCodec.getOutputFormat();
                            Log.i(TAG, mMediaFormat.toString());
                        } else if (mIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            Log.v(TAG, "No buffer available...");
                            //return 0;
                        } else {
                            Log.e(TAG, "Message: " + mIndex);
                            //return 0;
                        }
                    }
                }

                if (mClosed) {
                    throw new IOException("This InputStream was closed");
                }

                min = length < mBufferInfo.size - mBuffer.position() ? length : mBufferInfo.size - mBuffer.position();
                mBuffer.get(buffer, offset, min);
                if (mBuffer.position() >= mBufferInfo.size) {
                    mMediaCodec.releaseOutputBuffer(mIndex, false);
                    mBuffer = null;
                }

                // 将buffer写入到本地
                if (TEST_ENCODE) {
                    writeDataToLocal(buffer);
                }

            } catch (RuntimeException e) {
                Log.e(TAG, "Runtime exception happened while read data", e);
            }
            return min;
        }
    }

    private static File sLocalFile;
    private static OutputStream sOutputStream;

    private void writeDataToLocal(byte[] data) {
        if (sLocalFile == null) {
            sLocalFile = new File(Environment.getExternalStorageDirectory(), "video.cache");
        }
        if (sOutputStream == null) {
            try {
                sOutputStream = new BufferedOutputStream(new FileOutputStream(sLocalFile));
            } catch (FileNotFoundException e) {
                Log.e(TAG, "fail to find the data", e);
                return;
            }
        }

        try {
            Log.d(TAG, "write data of " + data.length + " to local");
            sOutputStream.write(data);
        } catch (IOException e) {
            Log.e(TAG, "IOException happened while read data", e);
        }

        if (mClosed) {
            if (sOutputStream != null) {
                try {
                    sOutputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "IOException happened while close the output stream", e);
                }
            }
        }
    }


    public int available() {
        if (mBuffer != null) {
            return mBufferInfo.size - mBuffer.position();
        } else {
            return 0;
        }
    }

    BufferInfo getLastBufferInfo() {
        return mBufferInfo;
    }

}
