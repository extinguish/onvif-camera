package net.majorkernelpanic.onvif;

public class ONVIFDevDiscoveryReqHeader {

    private String messageId;
    private String action;

    public ONVIFDevDiscoveryReqHeader(String messageId, String action) {
        this.messageId = messageId;
        this.action = action;
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
}
