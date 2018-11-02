package com.adasplus.data;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * FIXME: guoshichao 这里的{@link AdasConfig}当中包含了Adas本身的配置选项，但是同时也包含了
 * 关于DFW功能的配置选项，按照设计来说，这两种功能是需要分开处理的，所以在我们完成初步重构之后，需要
 * 将这里也单独处理.
 */
public class AdasConfig implements Parcelable {

    private int isCalibCredible;

    private float x, y;

    private float vehicleHeight;

    private float vehicleWidth;

    private int ldwSensitivity;

    private int fcwSensitivity;

    private int pedSensitivity;

    private int dfwSensitivity;

    private int ldwMinVelocity;

    private int fcwMinVelocity;

    private int pedMinVelocity;

    private int dfwMinVelocity;

    private int isLdwEnable;

    private int isFcwEnable;

    private int isStopgoEnable;

    private int isPedEnable;

    private int isDfwEnable;

    public AdasConfig() {

    }

    protected AdasConfig(Parcel in) {
        isCalibCredible = in.readInt();
        x = in.readFloat();
        y = in.readFloat();
        vehicleHeight = in.readFloat();
        vehicleWidth = in.readFloat();
        ldwSensitivity = in.readInt();
        fcwSensitivity = in.readInt();
        pedSensitivity = in.readInt();
        dfwSensitivity = in.readInt();
        ldwMinVelocity = in.readInt();
        fcwMinVelocity = in.readInt();
        pedMinVelocity = in.readInt();
        dfwMinVelocity = in.readInt();
        isLdwEnable = in.readInt();
        isFcwEnable = in.readInt();
        isStopgoEnable = in.readInt();
        isPedEnable = in.readInt();
        isDfwEnable = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(isCalibCredible);
        dest.writeFloat(x);
        dest.writeFloat(y);
        dest.writeFloat(vehicleHeight);
        dest.writeFloat(vehicleWidth);
        dest.writeInt(ldwSensitivity);
        dest.writeInt(fcwSensitivity);
        dest.writeInt(pedSensitivity);
        dest.writeInt(dfwSensitivity);
        dest.writeInt(ldwMinVelocity);
        dest.writeInt(fcwMinVelocity);
        dest.writeInt(pedMinVelocity);
        dest.writeInt(dfwMinVelocity);
        dest.writeInt(isLdwEnable);
        dest.writeInt(isFcwEnable);
        dest.writeInt(isStopgoEnable);
        dest.writeInt(isPedEnable);
        dest.writeInt(isDfwEnable);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<AdasConfig> CREATOR = new Creator<AdasConfig>() {
        @Override
        public AdasConfig createFromParcel(Parcel in) {
            return new AdasConfig(in);
        }

        @Override
        public AdasConfig[] newArray(int size) {
            return new AdasConfig[size];
        }
    };

    public int getIsCalibCredible() {
        return isCalibCredible;
    }

    public void setIsCalibCredible(int isCalibCredible) {
        this.isCalibCredible = isCalibCredible;
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public float getVehicleHeight() {
        return vehicleHeight;
    }

    public void setVehicleHeight(float vehicleHeight) {
        this.vehicleHeight = vehicleHeight;
    }

    public float getVehicleWidth() {
        return vehicleWidth;
    }

    public void setVehicleWidth(float vehicleWidth) {
        this.vehicleWidth = vehicleWidth;
    }

    public int getLdwSensitivity() {
        return ldwSensitivity;
    }

    public void setLdwSensitivity(int ldwSensitivity) {
        this.ldwSensitivity = ldwSensitivity;
    }

    public int getFcwSensitivity() {
        return fcwSensitivity;
    }

    public void setFcwSensitivity(int fcwSensitivity) {
        this.fcwSensitivity = fcwSensitivity;
    }

    public int getPedSensitivity() {
        return pedSensitivity;
    }

    public void setPedSensitivity(int pedSensitivity) {
        this.pedSensitivity = pedSensitivity;
    }

    public int getDfwSensitivity() {
        return dfwSensitivity;
    }

    public void setDfwSensitivity(int dfwSensitivity) {
        this.dfwSensitivity = dfwSensitivity;
    }

    public int getLdwMinVelocity() {
        return ldwMinVelocity;
    }

    public void setLdwMinVelocity(int ldwMinVelocity) {
        this.ldwMinVelocity = ldwMinVelocity;
    }

    public int getFcwMinVelocity() {
        return fcwMinVelocity;
    }

    public void setFcwMinVelocity(int fcwMinVelocity) {
        this.fcwMinVelocity = fcwMinVelocity;
    }

    public int getPedMinVelocity() {
        return pedMinVelocity;
    }

    public void setPedMinVelocity(int pedMinVelocity) {
        this.pedMinVelocity = pedMinVelocity;
    }

    public int getDfwMinVelocity() {
        return dfwMinVelocity;
    }

    public void setDfwMinVelocity(int dfwMinVelocity) {
        this.dfwMinVelocity = dfwMinVelocity;
    }

    public int getIsLdwEnable() {
        return isLdwEnable;
    }

    public void setIsLdwEnable(int isLdwEnable) {
        this.isLdwEnable = isLdwEnable;
    }

    public int getIsFcwEnable() {
        return isFcwEnable;
    }

    public void setIsFcwEnable(int isFcwEnable) {
        this.isFcwEnable = isFcwEnable;
    }

    public int getIsStopgoEnable() {
        return isStopgoEnable;
    }

    public void setIsStopgoEnable(int isStopgoEnable) {
        this.isStopgoEnable = isStopgoEnable;
    }

    public int getIsPedEnable() {
        return isPedEnable;
    }

    public void setIsPedEnable(int isPedEnable) {
        this.isPedEnable = isPedEnable;
    }

    public int getIsDfwEnable() {
        return isDfwEnable;
    }

    public void setIsDfwEnable(int isDfwEnable) {
        this.isDfwEnable = isDfwEnable;
    }

    @Override
    public String toString() {
        return "AdasConfig{" +
                "isCalibCredible=" + isCalibCredible +
                ", x=" + x +
                ", y=" + y +
                ", vehicleHeight=" + vehicleHeight +
                ", vehicleWidth=" + vehicleWidth +
                ", ldwSensitivity=" + ldwSensitivity +
                ", fcwSensitivity=" + fcwSensitivity +
                ", pedSensitivity=" + pedSensitivity +
                ", dfwSensitivity=" + dfwSensitivity +
                ", ldwMinVelocity=" + ldwMinVelocity +
                ", fcwMinVelocity=" + fcwMinVelocity +
                ", pedMinVelocity=" + pedMinVelocity +
                ", dfwMinVelocity=" + dfwMinVelocity +
                ", isLdwEnable=" + isLdwEnable +
                ", isFcwEnable=" + isFcwEnable +
                ", isStopgoEnable=" + isStopgoEnable +
                ", isPedEnable=" + isPedEnable +
                ", isDfwEnable=" + isDfwEnable +
                '}';
    }
}
