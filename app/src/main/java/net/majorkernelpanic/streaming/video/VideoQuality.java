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

import java.util.Iterator;
import java.util.List;

import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.util.Log;

/**
 * A class that represents the quality of a video stream.
 * It contains the resolution, the framerate (in fps) and the bitrate (in bps) of the stream.
 */
public class VideoQuality {

    public final static String TAG = "VideoQuality";

    /**
     * 对于我们自己的应用来说,传输的视频的宽,高,帧率以及比特率都是固定的.
     */
    public static final int VIDEO_RES_X = 640;
    public static final int VIDEO_RES_Y = 480;
    public static final int VIDEO_FRAME_RATE = 15;
    public static final int VIDEO_BITRATE = 250000;

    /**
     * Default video stream quality.
     */
    public final static VideoQuality DEFAULT_VIDEO_QUALITY =
            new VideoQuality(VIDEO_RES_X, VIDEO_RES_Y, VIDEO_FRAME_RATE, VIDEO_BITRATE);

    /**
     * Represents a quality for a video stream.
     */
    public VideoQuality() {
    }

    /**
     * Represents a quality for a video stream.
     *
     * @param resX The horizontal resolution
     * @param resY The vertical resolution
     */
    public VideoQuality(int resX, int resY) {
        this.resX = resX;
        this.resY = resY;
    }

    /**
     * Represents a quality for a video stream.
     *
     * @param resX      The horizontal resolution
     * @param resY      The vertical resolution
     * @param framerate The framerate in frame per seconds
     * @param bitrate   The bitrate in bit per seconds
     */
    public VideoQuality(int resX, int resY, int framerate, int bitrate) {
        this.framerate = framerate;
        this.bitrate = bitrate;
        this.resX = resX;
        this.resY = resY;
    }

    public int framerate = 0;
    public int bitrate = 0;
    public int resX = 0;
    public int resY = 0;

    public boolean equals(VideoQuality quality) {
        if (quality == null) return false;
        return (quality.resX == this.resX &
                quality.resY == this.resY &
                quality.framerate == this.framerate &
                quality.bitrate == this.bitrate);
    }

    public VideoQuality clone() {
        return new VideoQuality(resX, resY, framerate, bitrate);
    }

    public static VideoQuality parseQuality(String str) {
        VideoQuality quality = DEFAULT_VIDEO_QUALITY.clone();
        if (str != null) {
            String[] config = str.split("-");
            try {
                quality.bitrate = Integer.parseInt(config[0]) * 1000; // conversion to bit/s
                quality.framerate = Integer.parseInt(config[1]);
                quality.resX = Integer.parseInt(config[2]);
                quality.resY = Integer.parseInt(config[3]);
            } catch (IndexOutOfBoundsException e) {
                Log.e(TAG, "Exception happened while parse the quality", e);
            }
        }
        return quality;
    }

    /**
     * Checks if the requested resolution is supported by the camera.
     * If not, it modifies it by supported parameters.
     **/
    public static VideoQuality determineClosestSupportedResolution(Camera.Parameters parameters, VideoQuality quality) {
        VideoQuality v = quality.clone();
        int minDist = Integer.MAX_VALUE;
        StringBuilder supportedSizesStr = new StringBuilder("Supported resolutions: ");
        List<Size> supportedSizes = parameters.getSupportedPreviewSizes();
        for (Iterator<Size> it = supportedSizes.iterator(); it.hasNext(); ) {
            Size size = it.next();
            supportedSizesStr.append(size.width).append("x").append(size.height).append(it.hasNext() ? ", " : "");
            int dist = Math.abs(quality.resX - size.width);
            if (dist < minDist) {
                minDist = dist;
                v.resX = size.width;
                v.resY = size.height;
            }
        }
        Log.v(TAG, supportedSizesStr.toString());
        if (quality.resX != v.resX || quality.resY != v.resY) {
            Log.v(TAG, "Resolution modified: " + quality.resX + "x" + quality.resY + "->" + v.resX + "x" + v.resY);
        }

        return v;
    }

    public static int[] determineMaximumSupportedFramerate(Camera.Parameters parameters) {
        int[] maxFps = new int[]{0, 0};
        StringBuilder supportedFpsRangesStr = new StringBuilder("Supported frame rates: ");
        List<int[]> supportedFpsRanges = parameters.getSupportedPreviewFpsRange();
        for (Iterator<int[]> it = supportedFpsRanges.iterator(); it.hasNext(); ) {
            int[] interval = it.next();
            supportedFpsRangesStr
                    .append(interval[0] / 1000)
                    .append("-")
                    .append(interval[1] / 1000)
                    .append("fps")
                    .append(it.hasNext() ? ", " : "");
            if (interval[1] > maxFps[1] || (interval[0] > maxFps[0] && interval[1] == maxFps[1])) {
                maxFps = interval;
            }
        }
        Log.v(TAG, supportedFpsRangesStr.toString());
        return maxFps;
    }

}
