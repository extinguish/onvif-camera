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

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.support.annotation.NonNull;
import android.util.Log;

import net.majorkernelpanic.spydroid.Utilities;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

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
    private boolean mClosed = false;

    public MediaFormat mMediaFormat;

    public MediaCodecInputStream(MediaCodec mediaCodec) {
        mMediaCodec = mediaCodec;
        mBuffers = mMediaCodec.getOutputBuffers();
    }

    @Override
    public void close() {
        mClosed = true;
    }

    @Override
    public int read() throws IOException {
        return 0;
    }

    @SuppressLint("WrongConstant")
    @Override
    public int read(@NonNull byte[] buffer, int offset, int length) throws IOException {
        int min = 0;
        try {
            if (mBuffer == null) {
                while (!Thread.interrupted() && !mClosed) {
                    mIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 500000);
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
                    } else {
                        Log.e(TAG, "Message: " + mIndex);
                    }
                }
            }

            if (mClosed) throw new IOException("This InputStream was closed");

            min = length < mBufferInfo.size - mBuffer.position() ? length : mBufferInfo.size - mBuffer.position();
            mBuffer.get(buffer, offset, min);
            if (mBuffer.position() >= mBufferInfo.size) {
                mMediaCodec.releaseOutputBuffer(mIndex, false);
                mBuffer = null;
            }

            Log.d(TAG, "encoded data -------------------------------------------------------> ");
            // Utilities.printByteArr(TAG, buffer);

        } catch (RuntimeException e) {
            Log.e(TAG, "RuntimeException happened while we read the encoded data", e);
        }

        return min;
    }

    public int available() {
        if (mBuffer != null)
            return mBufferInfo.size - mBuffer.position();
        else
            return 0;
    }

    public BufferInfo getLastBufferInfo() {
        return mBufferInfo;
    }


}
