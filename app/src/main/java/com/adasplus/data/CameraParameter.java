package com.adasplus.data;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by fengyin on 17-6-28.
 */
public class CameraParameter implements Parcelable {
    private int gndx;
    private int gndy;
    private float distance;
    private float cameraHeight;
    private float cameraToBumper;
    private float cameraToLeftWheel;
    private float cameraToRightWhell;
    private float cameraToFrontAxle;

    protected CameraParameter(Parcel in) {
        gndx = in.readInt();
        gndy = in.readInt();
        distance = in.readFloat();
        cameraHeight = in.readFloat();
        cameraToBumper = in.readFloat();
        cameraToLeftWheel = in.readFloat();
        cameraToRightWhell = in.readFloat();
        cameraToFrontAxle = in.readFloat();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(gndx);
        dest.writeInt(gndy);
        dest.writeFloat(distance);
        dest.writeFloat(cameraHeight);
        dest.writeFloat(cameraToBumper);
        dest.writeFloat(cameraToLeftWheel);
        dest.writeFloat(cameraToRightWhell);
        dest.writeFloat(cameraToFrontAxle);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<CameraParameter> CREATOR = new Creator<CameraParameter>() {
        @Override
        public CameraParameter createFromParcel(Parcel in) {
            return new CameraParameter(in);
        }

        @Override
        public CameraParameter[] newArray(int size) {
            return new CameraParameter[size];
        }
    };

    public int getGndx() {
        return gndx;
    }

    public void setGndx(int gndx) {
        this.gndx = gndx;
    }

    public int getGndy() {
        return gndy;
    }

    public void setGndy(int gndy) {
        this.gndy = gndy;
    }

    public float getDistance() {
        return distance;
    }

    public void setDistance(float distance) {
        this.distance = distance;
    }

    public float getCameraHeight() {
        return cameraHeight;
    }

    public void setCameraHeight(float cameraHeight) {
        this.cameraHeight = cameraHeight;
    }

    public float getCameraToBumper() {
        return cameraToBumper;
    }

    public void setCameraToBumper(float cameraToBumper) {
        this.cameraToBumper = cameraToBumper;
    }

    public float getCameraToLeftWheel() {
        return cameraToLeftWheel;
    }

    public void setCameraToLeftWheel(float cameraToLeftWheel) {
        this.cameraToLeftWheel = cameraToLeftWheel;
    }

    public float getCameraToRightWhell() {
        return cameraToRightWhell;
    }

    public void setCameraToRightWhell(float cameraToRightWhell) {
        this.cameraToRightWhell = cameraToRightWhell;
    }

    public float getCameraToFrontAxle() {
        return cameraToFrontAxle;
    }

    public void setCameraToFrontAxle(float cameraToFrontAxle) {
        this.cameraToFrontAxle = cameraToFrontAxle;
    }

    @Override
    public String toString() {
        return "CameraParameter{" +
                "gndx=" + gndx +
                ", gndy=" + gndy +
                ", distance=" + distance +
                ", cameraHeight=" + cameraHeight +
                ", cameraToBumper=" + cameraToBumper +
                ", cameraToLeftWheel=" + cameraToLeftWheel +
                ", cameraToRightWhell=" + cameraToRightWhell +
                ", cameraToFrontAxle=" + cameraToFrontAxle +
                '}';
    }
}
