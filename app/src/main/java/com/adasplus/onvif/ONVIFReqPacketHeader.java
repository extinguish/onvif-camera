package com.adasplus.onvif;

public class ONVIFReqPacketHeader {

    private String messageId;
    private String action;

    private String userName;
    private String userPsw;
    private String getCapabilitiesNonce;

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
}
