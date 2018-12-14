package com.adasplus.onvif;

public class ONVIFReqPacketHeader {

    private String messageId;
    private String action;

    private String userName;
    private String userPsw;
    private String getCapabilitiesNonce;

    private String hour;
    private String minute;
    private String second;
    private String year;
    private String month;
    private String day;

    private String osdToken;

    public ONVIFReqPacketHeader() {
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }


    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserPsw() {
        return userPsw;
    }

    public void setUserPsw(String userPsw) {
        this.userPsw = userPsw;
    }

    public String getCapabilitiesNonce() {
        return getCapabilitiesNonce;
    }

    public void setCapabilitiesNonce(String capabilitiesNonce) {
        this.getCapabilitiesNonce = capabilitiesNonce;
    }

    public String getHour() {
        return hour;
    }

    public void setHour(String hour) {
        this.hour = hour;
    }

    public String getMinute() {
        return minute;
    }

    public void setMinute(String minute) {
        this.minute = minute;
    }

    public String getSecond() {
        return second;
    }

    public void setSecond(String second) {
        this.second = second;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public String getDay() {
        return day;
    }

    public void setDay(String day) {
        this.day = day;
    }

    public String getOsdToken() {
        return osdToken;
    }

    public void setOsdToken(String osdToken) {
        this.osdToken = osdToken;
    }
}
